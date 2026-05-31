package com.flz.flz_chat.data.remote.dto;

import java.util.List;

public class PageResult<T> {
    public long total;
    public int page;
    public int size;
    public List<T> records;
}
