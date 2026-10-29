package com.alarm.application.port;

/** 标识生成端口（UUID 语义）。 */
public interface IdGenerator {
    String nextId();
}
