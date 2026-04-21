package com.basisttha.service;

import com.basisttha.exception.UserException;
import com.basisttha.model.User;

public interface UserService {

    User findByJwtToken(String token) throws UserException;
}
