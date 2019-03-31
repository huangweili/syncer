package com.github.zzt93.syncer.data;

import java.util.HashMap;

/**
 * Config operation interface
 * @author zzt
 */
public interface SyncData {

  Object getId();

  SyncData setId(Object id);

  String getEntity();

  boolean isWrite();

  /**
   * @deprecated {@link #updated()} might be better
   * @return whether this event type is {@link SimpleEventType}
   */
  boolean isUpdate();

  boolean isDelete();

  boolean toWrite();

  boolean toUpdate();

  boolean toDelete();

  SyncData setEntity(String entity);

  String getRepo();

  SyncData setRepo(String repo);

  SimpleEventType getType();

  SyncData addExtra(String key, Object value);

  SyncData addField(String key, Object value);

  SyncData renameField(String oldKey, String newKey);

  SyncData removeField(String key);

  boolean removePrimaryKey();

  SyncData removeFields(String... keys);

  boolean containField(String key);

  SyncData updateField(String key, Object value);

  HashMap<String, Object> getFields();

  HashMap<String, Object> getExtras();

  Object getField(String key);

  String getEventId();

  String getDataId();

  SyncData setSourceIdentifier(String identifier);

  String getSourceIdentifier();

  HashMap<String, Object> getSyncBy();

  /**
   * update/delete by query
   */
  SyncByQuery syncByQuery();

  ExtraQuery extraQuery(String indexName, String typeName);

  boolean hasExtra();

  Object getExtra(String key);

  /**
   * create a new instance with meta info copied
   * @param index ith copy of original data, should be different across invocation because it is used for logging
   * @return a new instance of {@link SyncData}
   */
  SyncData copyMeta(int index);

  /**
   * Determine whether updated according to {@link java.util.Objects#deepEquals(Object, Object)}
   * @return any interested column is updated in this event
   * @see java.util.Objects#deepEquals(Object, Object)
   */
  boolean updated();

  /**
   * @param key column name
   * @return whether this key is {{@link #updated()}} in this event
   * @see java.util.Objects#deepEquals(Object, Object)
   */
  boolean updated(String key);

  Object getBefore(String key);

  /**
   * @return null if no before info (MongoDB update event has no before info now)
   */
  HashMap<String, Object> getBefore();

  @Override
  String toString();
}
