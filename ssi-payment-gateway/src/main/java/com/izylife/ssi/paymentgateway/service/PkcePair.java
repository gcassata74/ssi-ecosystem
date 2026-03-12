package com.izylife.ssi.paymentgateway.service;

public record PkcePair(String codeVerifier, String codeChallenge, String method) {
}
