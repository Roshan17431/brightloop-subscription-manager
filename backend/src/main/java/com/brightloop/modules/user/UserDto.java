package com.brightloop.modules.user;

public record UserDto(Long id, String name, String email, Role role, Long customerId) {}
