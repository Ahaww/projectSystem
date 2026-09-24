package edu.wylie.crs.common;

import java.util.List;

/** 与前端 api-contract 统一错误体：{ message, errors } */
public record ErrorBody(String message, List<ErrorItem> errors) {
}
