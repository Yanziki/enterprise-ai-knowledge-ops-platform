package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.knowledge.storage.StoredObject;

record AuthorizedDownload(StoredObject storedObject, String filename, String contentType) {}
