# Zham MCP server

Lets Claude (or any MCP client) read your Zham records and draft new ones.

The records live on the phone, so the server works on the CSV the app exports:

1. In the app: More -> Back up. Save `zham-backup.csv` on your computer.
2. Run the server (needs Python 3.10+ and `pip install mcp`).
3. To add a record, ask the assistant; it writes to `zham-inbox.csv`, a separate file.
   Import that file in the app (More -> Import). The backup is never modified, so
   nothing is imported twice.

Tools: `list_categories`, `list_entries`, `summary`, `add_entry`.

Claude Desktop / Claude Code config:

```json
{
  "mcpServers": {
    "zham": {
      "command": "python3",
      "args": ["/path/to/mcp/zham_mcp.py"],
      "env": {
        "ZHAM_BACKUP": "/path/to/zham-backup.csv",
        "ZHAM_INBOX": "/path/to/zham-inbox.csv",
        "ZHAM_TZ": "Asia/Yerevan"
      }
    }
  }
}
```

Limits: the backup holds records only, not plans or goals; the data is as fresh as your last export.
