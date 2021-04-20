package com.yofc.iot.apps.enip.commands;

public abstract class Command {

  private final CommandCode commandCode;

  protected Command(CommandCode commandCode) {
    this.commandCode = commandCode;
  }

  public CommandCode getCommandCode() {
    return commandCode;
  }
}
