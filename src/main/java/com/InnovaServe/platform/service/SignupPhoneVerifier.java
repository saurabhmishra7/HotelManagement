package com.InnovaServe.platform.service;

/** Implement with an SMS provider; reject proofs for any number other than the requested phone. */
public interface SignupPhoneVerifier {
  void verify(String providerProof, String phone);
}
