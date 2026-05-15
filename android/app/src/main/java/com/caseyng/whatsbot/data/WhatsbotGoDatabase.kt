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
     */
    fun queryChats(): Cursor =
        db.rawQuery("SELECT jid, name, last_message_timestamp FROM wa_chats", null)

    /**
     * Returns a raw cursor over wa_messages for a given chat JID, ordered newest first.
     * Caller is responsible for closing the cursor.
     */
    fun queryMessages(jid: String): Cursor =
        db.rawQuery(
            "SELECT jid, message_id, sender_jid, timestamp, body, is_from_me " +
                "FROM wa_messages WHERE jid = ? ORDER BY timestamp DESC",
            arrayOf(jid)
        )

    fun close() {
        if (db.isOpen) db.close()
    }
}
