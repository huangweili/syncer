package com.github.zzt93.syncer.config.code;

import com.github.zzt93.syncer.data.SyncData;
import com.github.zzt93.syncer.data.util.MethodFilter;

import java.util.List;

/**
 * @author zzt
 */
public class Base implements MethodFilter {
  @Override
  public void filter(List<SyncData> list) {
    SyncData sync = list.get(0);
    sync.addExtra("suffix", "");
    switch (sync.getEntity()) {
      case "news":
        sync.updateField("thumb_content", new String((byte[]) sync.getField("thumb_content"))).updateField("content", new String((byte[]) sync.getField("content")));
        break;
      case "types":
      case "simple_type":
        sync.updateField("text", new String((byte[]) sync.getField("text"))).updateField("tinyint", Byte.toUnsignedInt((Byte) sync.getField("tinyint")));
        sync.addExtra("suffix", "-" + ((int) sync.getField("tinyint"))/128);
        break;
      case "correctness":
        sync.updateField("type", Byte.toUnsignedInt((Byte) sync.getField("type")));
        break;
    }
  }
}
