package com.fasttrack.command;

import com.fasttrack.model.Command;
import com.fasttrack.model.Device;

import java.util.Collection;

public interface CommandSender {
    Collection<String> getSupportedCommands();
    void sendCommand(Device device, Command command) throws Exception;
}
