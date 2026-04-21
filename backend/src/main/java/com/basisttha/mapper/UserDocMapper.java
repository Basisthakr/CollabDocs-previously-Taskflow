package com.basisttha.mapper;

import com.basisttha.model.UserDoc;
import com.basisttha.response.UserDocDto;
import org.springframework.stereotype.Service;

@Service
public class UserDocMapper {

    public UserDocDto toUserDocDto(UserDoc userDoc) {
        return UserDocDto.builder()
                .username(userDoc.getUser().getDisplayName())
                .permission(userDoc.getPermission())
                .build();
    }

}
