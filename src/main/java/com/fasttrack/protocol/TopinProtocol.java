/*
 * Copyright 2019 - 2023 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fasttrack.protocol;

import com.fasttrack.BaseProtocol;
import com.fasttrack.PipelineBuilder;
import com.fasttrack.TrackerServer;
import com.fasttrack.config.Config;
import com.fasttrack.config.Keys;
import com.fasttrack.model.Command;

import jakarta.inject.Inject;

public class TopinProtocol extends BaseProtocol {

    @Inject
    public TopinProtocol(Config config) {
        if (!config.getBoolean(Keys.PROTOCOL_DISABLE_COMMANDS.withPrefix(getName()))) {
            setSupportedDataCommands(
                    Command.TYPE_SOS_NUMBER);
        }
        addServer(new TrackerServer(config, getName(), false) {
            @Override
            protected void addProtocolHandlers(PipelineBuilder pipeline, Config config) {
                pipeline.addLast(new TopinProtocolEncoder(TopinProtocol.this));
                pipeline.addLast(new TopinProtocolDecoder(TopinProtocol.this));
            }
        });
    }

}
