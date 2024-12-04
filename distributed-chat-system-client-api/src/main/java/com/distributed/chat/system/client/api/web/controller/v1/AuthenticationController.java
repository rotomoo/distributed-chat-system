package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.dto.authentication.SignupRequestDto;
import com.distributed.chat.system.client.api.web.service.AuthenticationService;
import com.distributed.chat.system.common.response.ResponseData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody @Valid SignupRequestDto signupRequestDto) {
        ResponseData responseData = authenticationService.signup(signupRequestDto);

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

}
