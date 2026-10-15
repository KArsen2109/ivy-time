"""Zham MCP server: lets an AI assistant read your time records and draft new ones.

Reads the backup CSV the app exports (More -> Export records to CSV). New records are never
written into that file; they go to a separate inbox CSV that you import in the
app (More -> Restore or import), so nothing is imported twice.

Environment:
  ZHAM_BACKUP  path to zham-backup.csv        (default ./zham-backup.csv)
  ZHAM_INBOX   path to the inbox CSV to write (default ./zham-inbox.csv)
  ZHAM_TZ      IANA zone for dates and times  (default: system zone)
"""
import csv
import os
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

try:  # mcp 1.x
    from mcp.server.fastmcp import FastMCP
except ModuleNotFoundError:  # mcp 2.x renamed it
    from mcp.server.mcpserver import MCPServer as FastMCP

HEADER = ["start", "end", "category", "project", "note", "description"]
BACKUP = Path(os.environ.get("ZHAM_BACKUP", "zham-backup.csv"))
INBOX = Path(os.environ.get("ZHAM_INBOX", "zham-inbox.csv"))
ZONE = ZoneInfo(os.environ["ZHAM_TZ"]) if os.environ.get("ZHAM_TZ") else datetime.now().astimezone().tzinfo

mcp = FastMCP("zham")


def _parse_utc(text: str) -> datetime | None:
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def _load() -> list[dict]:
    if not BACKUP.exists():
        raise FileNotFoundError(f"{BACKUP} not found. Export it in the app (More -> Export records to CSV) and set ZHAM_BACKUP.")
    rows = []
    with BACKUP.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            start = _parse_utc(row.get("start", ""))
            if start is None:
                continue  # same rule as the app: bad lines are skipped
            end = _parse_utc(row.get("end", "")) or start
            rows.append({
                "start": start.astimezone(ZONE),
                "end": end.astimezone(ZONE),
                "category": row.get("category") or "",
                "project": row.get("project") or "",
                "note": row.get("note") or "",
                "description": row.get("description") or "",
            })
    return sorted(rows, key=lambda r: r["start"])


def _range(date_from: str, date_to: str) -> tuple[datetime, datetime]:
    """Inclusive local dates (YYYY-MM-DD). Empty `date_to` means `date_from`; both empty means today."""
    first = date.fromisoformat(date_from) if date_from else datetime.now(ZONE).date()
    last = date.fromisoformat(date_to) if date_to else first
    return (datetime.combine(first, datetime.min.time(), ZONE),
            datetime.combine(last + timedelta(days=1), datetime.min.time(), ZONE))


def _select(date_from: str, date_to: str, category: str) -> list[dict]:
    lo, hi = _range(date_from, date_to)
    return [r for r in _load()
            if lo <= r["start"] < hi and (not category or r["category"].lower() == category.lower())]


def _hours(row: dict) -> float:
    return round((row["end"] - row["start"]).total_seconds() / 3600, 2)


@mcp.tool()
def list_categories() -> list[str]:
    """Category names that appear in the backup."""
    return sorted({r["category"] for r in _load() if r["category"]})


@mcp.tool()
def list_entries(date_from: str = "", date_to: str = "", category: str = "") -> list[dict]:
    """Time records between two local dates (YYYY-MM-DD, inclusive; default today), optionally one category."""
    return [{
        "start": r["start"].isoformat(timespec="minutes"),
        "end": r["end"].isoformat(timespec="minutes"),
        "hours": _hours(r),
        "category": r["category"],
        "project": r["project"],
        "note": r["note"],
        "description": r["description"],
    } for r in _select(date_from, date_to, category)]


@mcp.tool()
def summary(date_from: str = "", date_to: str = "", category: str = "") -> dict:
    """Total hours between two local dates, split by category, project and day."""
    rows = _select(date_from, date_to, category)
    by_category, by_project, by_day = defaultdict(float), defaultdict(float), defaultdict(float)
    for r in rows:
        hours = _hours(r)
        by_category[r["category"] or "(none)"] += hours
        if r["project"]:
            by_project[f'{r["category"] or "(none)"} / {r["project"]}'] += hours
        by_day[r["start"].date().isoformat()] += hours

    def top(d):
        return {k: round(v, 2) for k, v in sorted(d.items(), key=lambda kv: -kv[1])}

    return {
        "total_hours": round(sum(by_category.values()), 2),
        "records": len(rows),
        "by_category": top(by_category),
        "by_project": top(by_project),
        "by_day": dict(sorted((k, round(v, 2)) for k, v in by_day.items())),
    }


@mcp.tool()
def add_entry(start: str, end: str, category: str, project: str = "", note: str = "", description: str = "") -> str:
    """Draft a new record in the inbox file. `start`/`end` are local 'YYYY-MM-DD HH:MM'. Import the inbox in the app to add it."""
    begin = datetime.strptime(start, "%Y-%m-%d %H:%M").replace(tzinfo=ZONE)
    finish = datetime.strptime(end, "%Y-%m-%d %H:%M").replace(tzinfo=ZONE)
    if finish <= begin:
        raise ValueError("end must be after start")
    fresh = not INBOX.exists()
    with INBOX.open("a", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        if fresh:
            writer.writerow(HEADER)
        stamp = lambda d: d.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        writer.writerow([stamp(begin), stamp(finish), category, project, note, description])
    return f"Saved {category} {start} - {end} to {INBOX}. Import that file in the app (More -> Restore or import)."


if __name__ == "__main__":
    mcp.run()
