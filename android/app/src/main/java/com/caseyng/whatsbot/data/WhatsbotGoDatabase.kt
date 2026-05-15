package com.caseyng.whatsbot.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * Read-only accessor for the SQLite database written by the Go whatsmeow library.
 *
 * Schema is owned by the Go library — this class must never run migrations or DDL.
 *
 * Tables of interest:
 *   wa_chats    — columns: jid, name, last_message_timestamp
 *   wa_messages — columns: jid, message_id, sender_jid, timestamp, body, is_from_me
 *
 * Full implementation deferred to the contact picker phase.
 */
class WhatsbotGoDatabase(private val dbPath: String) {

    private val db: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    /**
     * Returns a raw cursor over wa_chats. Caller is responsible for closing the cursor.
     * Column order: jid, name, last_msg_ts
     */
    fun queryChats(): Cursor =
        db.rawQuery("SELECT jid, name, last_msg_ts FROM wa_chats ORDER BY last_msg_ts DESC", null)

    /**
     * Returns a raw cursor over wa_messages for a given chat JID, ordered newest first.
     * Caller is responsible for closing the cursor.
     * Column order: id, chat_jid, sender_jid, timestamp, body, is_from_me
     */
    fun queryMessages(chatJid: String): Cursor =
        db.rawQuery(
            "SELECT id, chat_jid, sender_jid, timestamp, body, is_from_me " +
                "FROM wa_messages WHERE chat_jid = ? ORDER BY timestamp DESC",
            arrayOf(chatJid)
        )

    fun close() {
        if (db.isOpen) db.close()
    }
}
