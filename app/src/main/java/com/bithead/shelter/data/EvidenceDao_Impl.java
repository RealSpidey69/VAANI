package com.bithead.shelter.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EvidenceDao_Impl implements EvidenceDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Evidence> __insertionAdapterOfEvidence;

  private final SharedSQLiteStatement __preparedStmtOfRequestDeletion;

  private final SharedSQLiteStatement __preparedStmtOfFinishDeletion;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLocation;

  public EvidenceDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEvidence = new EntityInsertionAdapter<Evidence>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `evidence` (`id`,`createdAt`,`encryptedFile`,`latitude`,`longitude`,`threatLabel`,`threatScore`,`sha256`,`previousHash`,`deletedFileHash`,`deletedAt`,`incidentId`,`mediaType`,`mimeType`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Evidence entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getCreatedAt());
        if (entity.getEncryptedFile() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getEncryptedFile());
        }
        if (entity.getLatitude() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getLatitude());
        }
        if (entity.getLongitude() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getLongitude());
        }
        if (entity.getThreatLabel() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getThreatLabel());
        }
        statement.bindLong(7, entity.getThreatScore());
        if (entity.getSha256() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getSha256());
        }
        if (entity.getPreviousHash() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getPreviousHash());
        }
        if (entity.getDeletedFileHash() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDeletedFileHash());
        }
        if (entity.getDeletedAt() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getDeletedAt());
        }
        if (entity.getIncidentId() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getIncidentId());
        }
        if (entity.getMediaType() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getMediaType());
        }
        if (entity.getMimeType() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getMimeType());
        }
      }
    };
    this.__preparedStmtOfRequestDeletion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE evidence SET deletedFileHash = ? WHERE id = ? AND deletedFileHash IS NULL AND deletedAt IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfFinishDeletion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE evidence SET deletedAt = ? WHERE id = ? AND deletedFileHash IS NOT NULL AND deletedAt IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLocation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE evidence SET latitude = ?, longitude = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final Evidence item, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfEvidence.insertAndReturnId(item);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertChained(final Evidence item, final String encryptedFileHash,
      final Continuation<? super Long> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> EvidenceDao.DefaultImpls.insertChained(EvidenceDao_Impl.this, item, encryptedFileHash, __cont), $completion);
  }

  @Override
  public Object requestDeletion(final long id, final String fileHash,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRequestDeletion.acquire();
        int _argIndex = 1;
        if (fileHash == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, fileHash);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRequestDeletion.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object finishDeletion(final long id, final long deletedAt,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfFinishDeletion.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, deletedAt);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfFinishDeletion.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLocation(final long id, final double latitude, final double longitude,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLocation.acquire();
        int _argIndex = 1;
        _stmt.bindDouble(_argIndex, latitude);
        _argIndex = 2;
        _stmt.bindDouble(_argIndex, longitude);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLocation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Evidence>> observeAll() {
    final String _sql = "SELECT * FROM evidence WHERE deletedAt IS NULL ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"evidence"}, new Callable<List<Evidence>>() {
      @Override
      @NonNull
      public List<Evidence> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfEncryptedFile = CursorUtil.getColumnIndexOrThrow(_cursor, "encryptedFile");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfThreatLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "threatLabel");
          final int _cursorIndexOfThreatScore = CursorUtil.getColumnIndexOrThrow(_cursor, "threatScore");
          final int _cursorIndexOfSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "sha256");
          final int _cursorIndexOfPreviousHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousHash");
          final int _cursorIndexOfDeletedFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedFileHash");
          final int _cursorIndexOfDeletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedAt");
          final int _cursorIndexOfIncidentId = CursorUtil.getColumnIndexOrThrow(_cursor, "incidentId");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<Evidence> _result = new ArrayList<Evidence>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Evidence _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpEncryptedFile;
            if (_cursor.isNull(_cursorIndexOfEncryptedFile)) {
              _tmpEncryptedFile = null;
            } else {
              _tmpEncryptedFile = _cursor.getString(_cursorIndexOfEncryptedFile);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final String _tmpThreatLabel;
            if (_cursor.isNull(_cursorIndexOfThreatLabel)) {
              _tmpThreatLabel = null;
            } else {
              _tmpThreatLabel = _cursor.getString(_cursorIndexOfThreatLabel);
            }
            final int _tmpThreatScore;
            _tmpThreatScore = _cursor.getInt(_cursorIndexOfThreatScore);
            final String _tmpSha256;
            if (_cursor.isNull(_cursorIndexOfSha256)) {
              _tmpSha256 = null;
            } else {
              _tmpSha256 = _cursor.getString(_cursorIndexOfSha256);
            }
            final String _tmpPreviousHash;
            if (_cursor.isNull(_cursorIndexOfPreviousHash)) {
              _tmpPreviousHash = null;
            } else {
              _tmpPreviousHash = _cursor.getString(_cursorIndexOfPreviousHash);
            }
            final String _tmpDeletedFileHash;
            if (_cursor.isNull(_cursorIndexOfDeletedFileHash)) {
              _tmpDeletedFileHash = null;
            } else {
              _tmpDeletedFileHash = _cursor.getString(_cursorIndexOfDeletedFileHash);
            }
            final Long _tmpDeletedAt;
            if (_cursor.isNull(_cursorIndexOfDeletedAt)) {
              _tmpDeletedAt = null;
            } else {
              _tmpDeletedAt = _cursor.getLong(_cursorIndexOfDeletedAt);
            }
            final String _tmpIncidentId;
            if (_cursor.isNull(_cursorIndexOfIncidentId)) {
              _tmpIncidentId = null;
            } else {
              _tmpIncidentId = _cursor.getString(_cursorIndexOfIncidentId);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            _item = new Evidence(_tmpId,_tmpCreatedAt,_tmpEncryptedFile,_tmpLatitude,_tmpLongitude,_tmpThreatLabel,_tmpThreatScore,_tmpSha256,_tmpPreviousHash,_tmpDeletedFileHash,_tmpDeletedAt,_tmpIncidentId,_tmpMediaType,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object latest(final Continuation<? super Evidence> $completion) {
    final String _sql = "SELECT * FROM evidence ORDER BY id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Evidence>() {
      @Override
      @Nullable
      public Evidence call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfEncryptedFile = CursorUtil.getColumnIndexOrThrow(_cursor, "encryptedFile");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfThreatLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "threatLabel");
          final int _cursorIndexOfThreatScore = CursorUtil.getColumnIndexOrThrow(_cursor, "threatScore");
          final int _cursorIndexOfSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "sha256");
          final int _cursorIndexOfPreviousHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousHash");
          final int _cursorIndexOfDeletedFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedFileHash");
          final int _cursorIndexOfDeletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedAt");
          final int _cursorIndexOfIncidentId = CursorUtil.getColumnIndexOrThrow(_cursor, "incidentId");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final Evidence _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpEncryptedFile;
            if (_cursor.isNull(_cursorIndexOfEncryptedFile)) {
              _tmpEncryptedFile = null;
            } else {
              _tmpEncryptedFile = _cursor.getString(_cursorIndexOfEncryptedFile);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final String _tmpThreatLabel;
            if (_cursor.isNull(_cursorIndexOfThreatLabel)) {
              _tmpThreatLabel = null;
            } else {
              _tmpThreatLabel = _cursor.getString(_cursorIndexOfThreatLabel);
            }
            final int _tmpThreatScore;
            _tmpThreatScore = _cursor.getInt(_cursorIndexOfThreatScore);
            final String _tmpSha256;
            if (_cursor.isNull(_cursorIndexOfSha256)) {
              _tmpSha256 = null;
            } else {
              _tmpSha256 = _cursor.getString(_cursorIndexOfSha256);
            }
            final String _tmpPreviousHash;
            if (_cursor.isNull(_cursorIndexOfPreviousHash)) {
              _tmpPreviousHash = null;
            } else {
              _tmpPreviousHash = _cursor.getString(_cursorIndexOfPreviousHash);
            }
            final String _tmpDeletedFileHash;
            if (_cursor.isNull(_cursorIndexOfDeletedFileHash)) {
              _tmpDeletedFileHash = null;
            } else {
              _tmpDeletedFileHash = _cursor.getString(_cursorIndexOfDeletedFileHash);
            }
            final Long _tmpDeletedAt;
            if (_cursor.isNull(_cursorIndexOfDeletedAt)) {
              _tmpDeletedAt = null;
            } else {
              _tmpDeletedAt = _cursor.getLong(_cursorIndexOfDeletedAt);
            }
            final String _tmpIncidentId;
            if (_cursor.isNull(_cursorIndexOfIncidentId)) {
              _tmpIncidentId = null;
            } else {
              _tmpIncidentId = _cursor.getString(_cursorIndexOfIncidentId);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            _result = new Evidence(_tmpId,_tmpCreatedAt,_tmpEncryptedFile,_tmpLatitude,_tmpLongitude,_tmpThreatLabel,_tmpThreatScore,_tmpSha256,_tmpPreviousHash,_tmpDeletedFileHash,_tmpDeletedAt,_tmpIncidentId,_tmpMediaType,_tmpMimeType);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object allAscending(final Continuation<? super List<Evidence>> $completion) {
    final String _sql = "SELECT * FROM evidence ORDER BY id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Evidence>>() {
      @Override
      @NonNull
      public List<Evidence> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfEncryptedFile = CursorUtil.getColumnIndexOrThrow(_cursor, "encryptedFile");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfThreatLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "threatLabel");
          final int _cursorIndexOfThreatScore = CursorUtil.getColumnIndexOrThrow(_cursor, "threatScore");
          final int _cursorIndexOfSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "sha256");
          final int _cursorIndexOfPreviousHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousHash");
          final int _cursorIndexOfDeletedFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedFileHash");
          final int _cursorIndexOfDeletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedAt");
          final int _cursorIndexOfIncidentId = CursorUtil.getColumnIndexOrThrow(_cursor, "incidentId");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final List<Evidence> _result = new ArrayList<Evidence>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Evidence _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpEncryptedFile;
            if (_cursor.isNull(_cursorIndexOfEncryptedFile)) {
              _tmpEncryptedFile = null;
            } else {
              _tmpEncryptedFile = _cursor.getString(_cursorIndexOfEncryptedFile);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final String _tmpThreatLabel;
            if (_cursor.isNull(_cursorIndexOfThreatLabel)) {
              _tmpThreatLabel = null;
            } else {
              _tmpThreatLabel = _cursor.getString(_cursorIndexOfThreatLabel);
            }
            final int _tmpThreatScore;
            _tmpThreatScore = _cursor.getInt(_cursorIndexOfThreatScore);
            final String _tmpSha256;
            if (_cursor.isNull(_cursorIndexOfSha256)) {
              _tmpSha256 = null;
            } else {
              _tmpSha256 = _cursor.getString(_cursorIndexOfSha256);
            }
            final String _tmpPreviousHash;
            if (_cursor.isNull(_cursorIndexOfPreviousHash)) {
              _tmpPreviousHash = null;
            } else {
              _tmpPreviousHash = _cursor.getString(_cursorIndexOfPreviousHash);
            }
            final String _tmpDeletedFileHash;
            if (_cursor.isNull(_cursorIndexOfDeletedFileHash)) {
              _tmpDeletedFileHash = null;
            } else {
              _tmpDeletedFileHash = _cursor.getString(_cursorIndexOfDeletedFileHash);
            }
            final Long _tmpDeletedAt;
            if (_cursor.isNull(_cursorIndexOfDeletedAt)) {
              _tmpDeletedAt = null;
            } else {
              _tmpDeletedAt = _cursor.getLong(_cursorIndexOfDeletedAt);
            }
            final String _tmpIncidentId;
            if (_cursor.isNull(_cursorIndexOfIncidentId)) {
              _tmpIncidentId = null;
            } else {
              _tmpIncidentId = _cursor.getString(_cursorIndexOfIncidentId);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            _item = new Evidence(_tmpId,_tmpCreatedAt,_tmpEncryptedFile,_tmpLatitude,_tmpLongitude,_tmpThreatLabel,_tmpThreatScore,_tmpSha256,_tmpPreviousHash,_tmpDeletedFileHash,_tmpDeletedAt,_tmpIncidentId,_tmpMediaType,_tmpMimeType);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object byId(final long id, final Continuation<? super Evidence> $completion) {
    final String _sql = "SELECT * FROM evidence WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Evidence>() {
      @Override
      @Nullable
      public Evidence call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfEncryptedFile = CursorUtil.getColumnIndexOrThrow(_cursor, "encryptedFile");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfThreatLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "threatLabel");
          final int _cursorIndexOfThreatScore = CursorUtil.getColumnIndexOrThrow(_cursor, "threatScore");
          final int _cursorIndexOfSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "sha256");
          final int _cursorIndexOfPreviousHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousHash");
          final int _cursorIndexOfDeletedFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedFileHash");
          final int _cursorIndexOfDeletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "deletedAt");
          final int _cursorIndexOfIncidentId = CursorUtil.getColumnIndexOrThrow(_cursor, "incidentId");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final Evidence _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpEncryptedFile;
            if (_cursor.isNull(_cursorIndexOfEncryptedFile)) {
              _tmpEncryptedFile = null;
            } else {
              _tmpEncryptedFile = _cursor.getString(_cursorIndexOfEncryptedFile);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final String _tmpThreatLabel;
            if (_cursor.isNull(_cursorIndexOfThreatLabel)) {
              _tmpThreatLabel = null;
            } else {
              _tmpThreatLabel = _cursor.getString(_cursorIndexOfThreatLabel);
            }
            final int _tmpThreatScore;
            _tmpThreatScore = _cursor.getInt(_cursorIndexOfThreatScore);
            final String _tmpSha256;
            if (_cursor.isNull(_cursorIndexOfSha256)) {
              _tmpSha256 = null;
            } else {
              _tmpSha256 = _cursor.getString(_cursorIndexOfSha256);
            }
            final String _tmpPreviousHash;
            if (_cursor.isNull(_cursorIndexOfPreviousHash)) {
              _tmpPreviousHash = null;
            } else {
              _tmpPreviousHash = _cursor.getString(_cursorIndexOfPreviousHash);
            }
            final String _tmpDeletedFileHash;
            if (_cursor.isNull(_cursorIndexOfDeletedFileHash)) {
              _tmpDeletedFileHash = null;
            } else {
              _tmpDeletedFileHash = _cursor.getString(_cursorIndexOfDeletedFileHash);
            }
            final Long _tmpDeletedAt;
            if (_cursor.isNull(_cursorIndexOfDeletedAt)) {
              _tmpDeletedAt = null;
            } else {
              _tmpDeletedAt = _cursor.getLong(_cursorIndexOfDeletedAt);
            }
            final String _tmpIncidentId;
            if (_cursor.isNull(_cursorIndexOfIncidentId)) {
              _tmpIncidentId = null;
            } else {
              _tmpIncidentId = _cursor.getString(_cursorIndexOfIncidentId);
            }
            final String _tmpMediaType;
            if (_cursor.isNull(_cursorIndexOfMediaType)) {
              _tmpMediaType = null;
            } else {
              _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            }
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            _result = new Evidence(_tmpId,_tmpCreatedAt,_tmpEncryptedFile,_tmpLatitude,_tmpLongitude,_tmpThreatLabel,_tmpThreatScore,_tmpSha256,_tmpPreviousHash,_tmpDeletedFileHash,_tmpDeletedAt,_tmpIncidentId,_tmpMediaType,_tmpMimeType);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object entryIdForFile(final String fileName,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT id FROM evidence WHERE encryptedFile = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (fileName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, fileName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getLong(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
