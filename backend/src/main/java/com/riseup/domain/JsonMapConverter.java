package com.riseup.domain;

import jakarta.persistence.Converter;

/** @deprecated use com.riseup.persistence.JsonMapConverter */
@Converter
@Deprecated
public class JsonMapConverter extends com.riseup.persistence.JsonMapConverter {}
