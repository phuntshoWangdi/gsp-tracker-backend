/*
 * Copyright 2017 - 2025 Anton Tananaev (anton@traccar.org)
 * Copyright 2017 Andrey Kunitsyn (andrey@traccar.org)
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
package com.fasttrack.database;

import com.fasttrack.BaseProtocol;
import com.fasttrack.ServerManager;
import com.fasttrack.broadcast.BroadcastInterface;
import com.fasttrack.broadcast.BroadcastService;
import com.fasttrack.command.CommandSenderManager;
import com.fasttrack.config.Keys;
import com.fasttrack.model.Command;
import com.fasttrack.model.Device;
import com.fasttrack.model.Event;
import com.fasttrack.model.ObjectOperation;
import com.fasttrack.model.Position;
import com.fasttrack.model.QueuedCommand;
import com.fasttrack.session.ConnectionManager;
import com.fasttrack.session.DeviceSession;
import com.fasttrack.session.cache.CacheManager;
import com.fasttrack.sms.SmsManager;
import com.fasttrack.storage.Storage;
import com.fasttrack.storage.StorageException;
import com.fasttrack.storage.query.Columns;
import com.fasttrack.storage.query.Condition;
import com.fasttrack.storage.query.Order;
import com.fasttrack.storage.query.Request;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class CommandsManager implements BroadcastInterface {

    private final Storage storage;
    private final ServerManager serverManager;
    private final SmsManager smsManager;
    private final ConnectionManager connectionManager;
    private final BroadcastService broadcastService;
    private final NotificationManager notificationManager;
    private final CacheManager cacheManager;
    private final CommandSenderManager commandSenderManager;

    @Inject
    public CommandsManager(
            Storage storage, ServerManager serverManager, @Nullable SmsManager smsManager,
            ConnectionManager connectionManager, BroadcastService broadcastService,
            NotificationManager notificationManager, CacheManager cacheManager,
            CommandSenderManager commandSenderManager) {
        this.storage = storage;
        this.serverManager = serverManager;
        this.smsManager = smsManager;
        this.connectionManager = connectionManager;
        this.broadcastService = broadcastService;
        this.notificationManager = notificationManager;
        this.cacheManager = cacheManager;
        this.commandSenderManager = commandSenderManager;
        broadcastService.registerListener(this);
    }

    public QueuedCommand sendCommand(Command command) throws Exception {
        long deviceId = command.getDeviceId();
        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", deviceId)));
        Position position = storage.getObject(Position.class, new Request(
                new Columns.All(), new Condition.Equals("id", device.getPositionId())));
        BaseProtocol protocol = position != null ? serverManager.getProtocol(position.getProtocol()) : null;

        if (command.getTextChannel()) {
            if (smsManager == null) {
                throw new RuntimeException("SMS not configured");
            }
            if (position != null) {
                protocol.sendTextCommand(device.getPhone(), command);
            } else if (command.getType().equals(Command.TYPE_CUSTOM)) {
                smsManager.sendMessage(device.getPhone(), command.getString(Command.KEY_DATA), true);
            } else {
                throw new RuntimeException("Command " + command.getType() + " is not supported");
            }
        } else {
            String sender = device.getString(Keys.COMMAND_SENDER.getKey());
            if (sender != null) {
                commandSenderManager.getSender(sender).sendCommand(device, command);
            } else {
                DeviceSession deviceSession = connectionManager.getDeviceSession(deviceId);
                if (deviceSession != null && deviceSession.supportsLiveCommands()) {
                    deviceSession.sendCommand(command);
                } else if (!command.getBoolean(Command.KEY_NO_QUEUE)) {
                    QueuedCommand queuedCommand = QueuedCommand.fromCommand(command);
                    queuedCommand.setId(storage.addObject(queuedCommand, new Request(new Columns.Exclude("id"))));
                    broadcastService.updateCommand(true, deviceId);
                    return queuedCommand;
                } else {
                    throw new RuntimeException("Failed to send command");
                }
            }
        }
        return null;
    }

    public Collection<Command> readQueuedCommands(long deviceId) {
        return readQueuedCommands(deviceId, Integer.MAX_VALUE);
    }

    public Collection<Command> readQueuedCommands(long deviceId, int count) {
        try {
            var commands = storage.getObjects(QueuedCommand.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("deviceId", deviceId),
                    new Order("id", false, count)));
            Map<Event, Position> events = new HashMap<>();
            for (var command : commands) {
                storage.removeObject(QueuedCommand.class, new Request(
                        new Condition.Equals("id", command.getId())));

                Event event = new Event(Event.TYPE_QUEUED_COMMAND_SENT, command.getDeviceId());
                event.set("id", command.getId());
                events.put(event, null);
            }
            notificationManager.updateEvents(events);
            return commands.stream().map(QueuedCommand::toCommand).collect(Collectors.toList());
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateCommand(boolean local, long deviceId) {
        if (!local) {
            DeviceSession deviceSession = connectionManager.getDeviceSession(deviceId);
            if (deviceSession != null && deviceSession.supportsLiveCommands()) {
                for (Command command : readQueuedCommands(deviceId)) {
                    deviceSession.sendCommand(command);
                }
            }
        }
    }

    public void updateNotificationToken(long deviceId, String token) {
        var key = new Object();
        try {
            cacheManager.addDevice(deviceId, key);
            Device device = cacheManager.getObject(Device.class, deviceId);
            device.set("notificationTokens", token);
            storage.updateObject(device, new Request(
                    new Columns.Include("attributes"),
                    new Condition.Equals("id", deviceId)));
            cacheManager.invalidateObject(true, Device.class, deviceId, ObjectOperation.UPDATE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            cacheManager.removeDevice(deviceId, key);
        }
    }

}
