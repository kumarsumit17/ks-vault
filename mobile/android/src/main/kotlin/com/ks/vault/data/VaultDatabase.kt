package com.ks.vault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Item::class, Attachment::class], version = 1, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        const val NAME = "vault.db"

        init {
            System.loadLibrary("sqlcipher")
        }

        /**
         * Opens the SQLCipher database with the 32-byte vault key, given as a raw key (`x'…'`), so
         * SQLCipher does not run its own key derivation on top. Throws when the key is wrong.
         */
        fun open(context: Context, vaultKey: ByteArray, name: String = NAME): VaultDatabase {
            val hex = vaultKey.joinToString("") { "%02x".format(it) }
            val passphrase = "x'$hex'".toByteArray(Charsets.US_ASCII)
            val db = Room.databaseBuilder(context, VaultDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
            // Open it now, so a wrong key fails here and not on the first query.
            db.openHelper.writableDatabase
            // SQLCipher keeps [passphrase] to open further connections, so it is not wiped here; it
            // becomes unreachable when the vault locks and the database is closed.
            return db
        }
    }
}
