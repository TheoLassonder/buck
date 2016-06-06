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

package com.facebook.buck.intellij.plugin.ws.buckevents.handlers;

import com.facebook.buck.event.external.events.BuckEventExternalInterface;
import com.facebook.buck.event.external.events.ProgressEventInterface;
import com.facebook.buck.intellij.plugin.ws.buckevents.consumers.BuckEventsConsumerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class BuckBuildProgressHandler implements BuckEventHandler {
  @Override
  public void handleEvent(
      String rawMessage,
      BuckEventExternalInterface event,
      BuckEventsConsumerFactory buckEventsConsumerFactory,
      ObjectMapper objectMapper) throws IOException {
    ProgressEventInterface buildProgressEvent =
        objectMapper.readValue(rawMessage,
            ProgressEventInterface.class);

    buckEventsConsumerFactory.getBuckBuildProgressUpdateConsumer()
        .consumeBuckBuildProgressUpdate(
            buildProgressEvent.getTimestamp(),
            buildProgressEvent.getProgressValue());
  }
}
