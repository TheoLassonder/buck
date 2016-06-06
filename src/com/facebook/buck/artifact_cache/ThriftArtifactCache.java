/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.artifact_cache;

import com.facebook.buck.artifact_cache.thrift.ArtifactMetadata;
import com.facebook.buck.artifact_cache.thrift.BuckCacheFetchRequest;
import com.facebook.buck.artifact_cache.thrift.BuckCacheFetchResponse;
import com.facebook.buck.artifact_cache.thrift.BuckCacheRequest;
import com.facebook.buck.artifact_cache.thrift.BuckCacheRequestType;
import com.facebook.buck.artifact_cache.thrift.BuckCacheResponse;
import com.facebook.buck.artifact_cache.thrift.BuckCacheStoreRequest;
import com.facebook.buck.artifact_cache.thrift.PayloadInfo;
import com.facebook.buck.io.LazyPath;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.slb.HttpResponse;
import com.facebook.buck.slb.ThriftProtocol;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import okio.BufferedSink;

/**
 * This is the Thrift protocol for the cache. The underlying channel is still HTTP but the
 * payload is Thrift.
 * To learn a bit more about the protocol please look at ThriftArtifactCacheProtocol.
 */
public class ThriftArtifactCache extends AbstractNetworkCache {

  private static final Logger LOG = Logger.get(ThriftArtifactCache.class);

  public static final MediaType HYBRID_THRIFT_STREAM_CONTENT_TYPE =
      MediaType.parse("application/x-hybrid-thrift-binary");
  public static final String PROTOCOL_HEADER = "X-Thrift-Protocol";
  public static final ThriftProtocol PROTOCOL = ThriftProtocol.COMPACT;

  private final String hybridThriftEndpoint;

  public ThriftArtifactCache(NetworkCacheArgs args) {
    super(args);
    Preconditions.checkArgument(
        args.getThriftEndpointPath().isPresent(),
        "Hybrid thrift endpoint path is mandatory for the ThriftArtifactCache.");
    this.hybridThriftEndpoint = args.getThriftEndpointPath().or("");
  }

  @Override
  public CacheResult fetchImpl(
      RuleKey ruleKey,
      LazyPath output,
      HttpArtifactCacheEvent.Finished.Builder eventBuilder) throws IOException {

    BuckCacheFetchRequest fetchRequest = new BuckCacheFetchRequest();
    com.facebook.buck.artifact_cache.thrift.RuleKey thriftRuleKey =
        new com.facebook.buck.artifact_cache.thrift.RuleKey();
    thriftRuleKey.setHashString(ruleKey.getHashCode().toString());
    fetchRequest.setRuleKey(thriftRuleKey);

    BuckCacheRequest cacheRequest = new BuckCacheRequest();
    cacheRequest.setType(BuckCacheRequestType.FETCH);
    cacheRequest.setFetchRequest(fetchRequest);

    final ThriftArtifactCacheProtocol.Request request =
        ThriftArtifactCacheProtocol.createRequest(PROTOCOL, cacheRequest);
    Request.Builder builder = toOkHttpRequest(request);
    try (HttpResponse httpResponse = fetchClient.makeRequest(hybridThriftEndpoint, builder)) {
      if (httpResponse.code() != 200) {
        String message = String.format(
            "Failed to fetch cache artifact with HTTP status code [%d] " +
                " to url [%s] for rule key [%s].",
            httpResponse.code(),
            httpResponse.requestUrl(),
            ruleKey.toString());
        LOG.error(message);
        return CacheResult.error(name, message);
      }

      try (ThriftArtifactCacheProtocol.Response response =
          ThriftArtifactCacheProtocol.parseResponse(PROTOCOL, httpResponse.getBody())) {

        BuckCacheResponse cacheResponse = response.getThriftData();
        if (!cacheResponse.isWasSuccessful()) {
          return CacheResult.error(name, cacheResponse.getErrorMessage());
        }

        BuckCacheFetchResponse fetchResponse = cacheResponse.getFetchResponse();
        if (!fetchResponse.isArtifactExists()) {
          return CacheResult.miss();
        }

        Path tmp = createTempFileForDownload();
        ThriftArtifactCacheProtocol.Response.ReadPayloadInfo readResult = null;
        try (OutputStream tmpFile = projectFilesystem.newFileOutputStream(tmp)) {
          readResult = response.readPayload(tmpFile);
        }

        ArtifactMetadata metadata = fetchResponse.getMetadata();
        if (!metadata.isSetArtifactPayloadCrc32()) {
          String msg = "Fetched artifact is missing the CRC32 hash.";
          LOG.error(msg);
          return CacheResult.error(name, msg);
        }

        if (!readResult.getCrc32Hash()
            .equals(fetchResponse.getMetadata().getArtifactPayloadCrc32())) {
          String msg = String.format(
              "The artifact fetched from cache is corrupted. ExpectedCRC32=[%s] ActualCRC32=[%s]",
              fetchResponse.getMetadata().getArtifactPayloadCrc32(),
              readResult.getCrc32Hash());
          LOG.error(msg);
          return CacheResult.error(name, msg);
        }

        // This makes sure we don't have 'half downloaded files' in the dir cache.
        projectFilesystem.move(tmp, output.get(), StandardCopyOption.REPLACE_EXISTING);
        return CacheResult.hit(
            name,
            ImmutableMap.copyOf(fetchResponse.getMetadata().getMetadata()),
            readResult.getBytesRead());
      }
    }
  }

  @Override
  protected void storeImpl(
      final ArtifactInfo info,
      final Path file,
      final HttpArtifactCacheEvent.Finished.Builder eventBuilder) throws IOException {

    final ByteSource artifact = new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return projectFilesystem.newFileInputStream(file);
      }
    };

    BuckCacheStoreRequest storeRequest = new BuckCacheStoreRequest();
    storeRequest.setMetadata(infoToMetadata(info, artifact));
    PayloadInfo payloadInfo = new PayloadInfo();
    payloadInfo.setSizeBytes(artifact.size());
    BuckCacheRequest cacheRequest = new BuckCacheRequest();
    cacheRequest.addToPayloads(payloadInfo);
    cacheRequest.setType(BuckCacheRequestType.STORE);
    cacheRequest.setStoreRequest(storeRequest);

    final ThriftArtifactCacheProtocol.Request request =
        ThriftArtifactCacheProtocol.createRequest(PROTOCOL, cacheRequest, artifact);
    Request.Builder builder = toOkHttpRequest(request);
    try (HttpResponse httpResponse = storeClient.makeRequest(hybridThriftEndpoint, builder)) {
      if (httpResponse.code() != 200) {
        throw new IOException(String.format(
            "Failed to store cache artifact with HTTP status code [%d] " +
                " to url [%s] for build target [%s] that has size [%d] bytes.",
            httpResponse.code(),
            httpResponse.requestUrl(),
            info.getBuildTarget().orNull(),
            artifact.size()));
      }

      try (ThriftArtifactCacheProtocol.Response response =
          ThriftArtifactCacheProtocol.parseResponse(PROTOCOL, httpResponse.getBody())) {
        if (!response.getThriftData().isWasSuccessful()) {
          reportFailure(
              "Failed to store artifact with thriftErrorMessage=[%s] " +
                  "url=[%s] artifactSizeBytes=[%d]",
              response.getThriftData().getErrorMessage(),
              httpResponse.requestUrl(),
              artifact.size());
        }
        eventBuilder.setWasUploadSuccessful(response.getThriftData().isWasSuccessful());
      }
    }
  }

  private Path createTempFileForDownload() throws IOException {
    projectFilesystem.mkdirs(projectFilesystem.getBuckPaths().getScratchDir());
    return  projectFilesystem.createTempFile(
        projectFilesystem.getBuckPaths().getScratchDir(),
        "buckcache_artifact",
        ".tmp");
  }

  private static ArtifactMetadata infoToMetadata(ArtifactInfo info, ByteSource file)
      throws IOException {
    ArtifactMetadata metadata = new ArtifactMetadata();
    if (info.getBuildTarget().isPresent()) {
      metadata.setBuildTarget(info.getBuildTarget().get().toString());
    }

    if (info.getRepository().isPresent()) {
      metadata.setRepository(info.getRepository().get());
    }

    metadata.setRuleKeys(ImmutableList.copyOf(Iterables.transform(
        info.getRuleKeys(),
        new Function<RuleKey, com.facebook.buck.artifact_cache.thrift.RuleKey>() {

          @Override
          public com.facebook.buck.artifact_cache.thrift.RuleKey apply(RuleKey input) {
            com.facebook.buck.artifact_cache.thrift.RuleKey ruleKey =
                new com.facebook.buck.artifact_cache.thrift.RuleKey();
            ruleKey.setHashString(input.getHashCode().toString());
            return ruleKey;
          }

        })));

    metadata.setMetadata(info.getMetadata());
    metadata.setArtifactPayloadCrc32(ThriftArtifactCacheProtocol.computeCrc32(file));

    return metadata;
  }

  private static Request.Builder toOkHttpRequest(
      final ThriftArtifactCacheProtocol.Request request) {
    Request.Builder builder = new Request.Builder()
        .addHeader(PROTOCOL_HEADER, PROTOCOL.toString().toLowerCase());
    builder.post(new RequestBody() {
      @Override
      public MediaType contentType() {
        return HYBRID_THRIFT_STREAM_CONTENT_TYPE;
      }

      @Override
      public long contentLength() throws IOException {
        return request.getRequestLengthBytes();
      }

      @Override
      public void writeTo(BufferedSink bufferedSink) throws IOException {
        request.writeAndClose(bufferedSink.outputStream());
      }
    });

    return builder;
  }
}
