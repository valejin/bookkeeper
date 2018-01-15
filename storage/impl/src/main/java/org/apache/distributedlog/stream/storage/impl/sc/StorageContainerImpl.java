/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.distributedlog.stream.storage.impl.sc;

import static org.apache.distributedlog.stream.protocol.ProtocolConstants.ROOT_STORAGE_CONTAINER_ID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.bookkeeper.common.concurrent.FutureUtils;
import org.apache.bookkeeper.common.util.OrderedScheduler;
import org.apache.distributedlog.clients.impl.internal.api.StorageServerClientManager;
import org.apache.distributedlog.stream.proto.storage.CreateNamespaceRequest;
import org.apache.distributedlog.stream.proto.storage.CreateNamespaceResponse;
import org.apache.distributedlog.stream.proto.storage.CreateStreamRequest;
import org.apache.distributedlog.stream.proto.storage.CreateStreamResponse;
import org.apache.distributedlog.stream.proto.storage.DeleteNamespaceRequest;
import org.apache.distributedlog.stream.proto.storage.DeleteNamespaceResponse;
import org.apache.distributedlog.stream.proto.storage.DeleteStreamRequest;
import org.apache.distributedlog.stream.proto.storage.DeleteStreamResponse;
import org.apache.distributedlog.stream.proto.storage.GetNamespaceRequest;
import org.apache.distributedlog.stream.proto.storage.GetNamespaceResponse;
import org.apache.distributedlog.stream.proto.storage.GetStreamRequest;
import org.apache.distributedlog.stream.proto.storage.GetStreamResponse;
import org.apache.distributedlog.stream.proto.storage.StorageContainerRequest;
import org.apache.distributedlog.stream.proto.storage.StorageContainerResponse;
import org.apache.distributedlog.stream.protocol.util.StorageContainerPlacementPolicy;
import org.apache.distributedlog.stream.storage.api.metadata.RootRangeStore;
import org.apache.distributedlog.stream.storage.api.sc.StorageContainer;
import org.apache.distributedlog.stream.storage.conf.StorageConfiguration;
import org.apache.distributedlog.stream.storage.impl.metadata.MetaRangeStoreImpl;
import org.apache.distributedlog.stream.storage.impl.metadata.RootRangeStoreImpl;

/**
 * The default implementation of {@link StorageContainer}.
 */
@Slf4j
public class StorageContainerImpl
    implements StorageContainer {

  private final long scId;
  private final ScheduledExecutorService scExecutor;

  // store container that used for fail requests.
  private final StorageContainer failRequestStorageContainer;
  // storage container
  private final MetaRangeStoreImpl mgStore;
  // root range
  private final RootRangeStore rootRange;

  public StorageContainerImpl(StorageConfiguration storageConf,
                              long scId,
                              StorageContainerPlacementPolicy rangePlacementPolicy,
                              OrderedScheduler scheduler,
                              StorageServerClientManager clientManager) {
    this.scId = scId;
    this.scExecutor = scheduler.chooseThread(scId);
    this.failRequestStorageContainer = FailRequestStorageContainer.of(scheduler);
    // set the root range store
    if (ROOT_STORAGE_CONTAINER_ID == scId) {
      RootRangeStoreImpl rsImpl = new RootRangeStoreImpl(
        clientManager,
        rangePlacementPolicy,
        scExecutor);
      this.rootRange = rsImpl;
    } else {
      this.rootRange = failRequestStorageContainer;
    }
    this.mgStore = new MetaRangeStoreImpl(
      storageConf,
      scId,
      clientManager,
      rangePlacementPolicy,
      scExecutor);
  }

  //
  // Services
  //

  @Override
  public long getId() {
    return scId;
  }

  @Override
  public CompletableFuture<Void> start() {
    log.info("Starting storage container ({}) ...", getId());
    return FutureUtils.value(null);
  }

  @Override
  public CompletableFuture<Void> stop() {
    return FutureUtils.value(null);
  }

  @Override
  public void close() {
    stop().join();
  }

  //
  // Storage Container API
  //

  @Override
  public CompletableFuture<StorageContainerResponse> addStreamMetaRange(StorageContainerRequest request) {
    return mgStore.addStreamMetaRange(request);
  }

  @Override
  public CompletableFuture<StorageContainerResponse> removeStreamMetaRange(StorageContainerRequest request) {
    return mgStore.removeStreamMetaRange(request);
  }

  //
  // Namespace API
  //

  @Override
  public CompletableFuture<CreateNamespaceResponse> createNamespace(CreateNamespaceRequest request) {
    return rootRange.createNamespace(request);
  }

  @Override
  public CompletableFuture<DeleteNamespaceResponse> deleteNamespace(DeleteNamespaceRequest request) {
    return rootRange.deleteNamespace(request);
  }

  @Override
  public CompletableFuture<GetNamespaceResponse> getNamespace(GetNamespaceRequest request) {
    return rootRange.getNamespace(request);
  }

  //
  // Stream API.
  //

  @Override
  public CompletableFuture<CreateStreamResponse> createStream(CreateStreamRequest request) {
    return rootRange.createStream(request);
  }

  @Override
  public CompletableFuture<DeleteStreamResponse> deleteStream(DeleteStreamRequest request) {
    return rootRange.deleteStream(request);
  }

  @Override
  public CompletableFuture<GetStreamResponse> getStream(GetStreamRequest request) {
    return rootRange.getStream(request);
  }

  //
  // Stream Meta Range API.
  //

  @Override
  public CompletableFuture<StorageContainerResponse> getActiveRanges(StorageContainerRequest request) {
    return mgStore.getActiveRanges(request);
  }

}
