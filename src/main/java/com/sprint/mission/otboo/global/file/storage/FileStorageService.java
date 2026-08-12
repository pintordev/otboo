package com.sprint.mission.otboo.global.file.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  String store(MultipartFile file, String domain);

  void delete(String key);
}
