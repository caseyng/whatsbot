package whatsbot

import (
	"database/sql"
	"time"
)

func initMessageTable(db *sql.DB) error {
	_, err := db.Exec(`
		CREATE TABLE IF NOT EXISTS wa_messages (
			id          TEXT PRIMARY KEY,
			chat_jid    TEXT NOT NULL,
			sender_jid  TEXT NOT NULL,
			timestamp   INTEGER NOT NULL,
			body        TEXT,
			media_type  TEXT,
			is_from_me  INTEGER NOT NULL DEFAULT 0,
			is_group    INTEGER NOT NULL DEFAULT 0
		);
		CREATE INDEX IF NOT EXISTS idx_wamsg_chat ON wa_messages(chat_jid, timestamp);
	`)
	return err
}

// storeMessage inserts a message row. Returns true if the row was inserted,
// false if it already existed (INSERT OR IGNORE) or if the insert failed.
func storeMessage(db *sql.DB, id, chatJID, senderJID string, ts time.Time,
	body, mediaType string, isFromMe, isGroup bool) bool {
	fromMe, grp := 0, 0
	if isFromMe {
		fromMe = 1
	}
	if isGroup {
		grp = 1
	}
	_, err := db.Exec(`
		INSERT OR IGNORE INTO wa_messages
			(id, chat_jid, sender_jid, timestamp, body, media_type, is_from_me, is_group)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		id, chatJID, senderJID, ts.Unix(), body, mediaType, fromMe, grp)
	return err == nil
}
