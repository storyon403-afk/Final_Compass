package cn.finalscompass.cet.domain;

import cn.finalscompass.controller.CetController.ItemInput;

/** CET 题目与音频命令的持久化端口 */
public interface CetItemRepository {
  String paperLevel(long paperId);
  boolean sectionExists(long paperId, String mode, String section);
  long createItem(ItemInput input);
  ItemIdentity itemIdentity(long itemId);
  void updateItem(long itemId, ItemInput input);
  boolean paperExists(long paperId);
  String replacePracticeAudio(long paperId, String storage, String original, String mimeType);
  boolean itemExists(long itemId);
  String replaceItemAudio(long itemId, String storage, String original, String mimeType);
  AudioDescriptor audio(long itemId);

  record ItemIdentity(long paperId, String mode, String section) {}
  record AudioDescriptor(String storageName, String originalName, String mimeType) {}
}
