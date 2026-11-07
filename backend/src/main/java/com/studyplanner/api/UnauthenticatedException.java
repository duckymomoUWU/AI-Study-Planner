package com.studyplanner.api;

/** Raised when a controller finds no authenticated principal. The handler answers 401 UNAUTHORIZED. */
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("Authentication required");
    }
}
