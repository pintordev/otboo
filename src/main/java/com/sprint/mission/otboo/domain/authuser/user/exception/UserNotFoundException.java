package com.sprint.mission.otboo.domain.authuser.user.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class UserNotFoundException extends UserException{

    private static final String MESSAGE = "사용자 정보를 찾을 수 없습니다.";

    private UserNotFoundException(Map<String, Object> details) {
        super(HttpStatus.NOT_FOUND, MESSAGE, details);
    }

    public static UserNotFoundException withNone() {
        return new UserNotFoundException(Collections.emptyMap());
    }
}
