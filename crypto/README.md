# crypto

> The Java Cryptography Architecture, read the way a security reviewer reads it.

Java's crypto API is a set of engine classes (`MessageDigest`, `Mac`, `Cipher`, `Signature`, `KeyAgreement`, `KeyPairGenerator`, `KeyStore`, `SSLContext`, ...) that you never instantiate directly. You ask for an algorithm by name, `Cipher.getInstance("AES/GCM/NoPadding")`, and the JCA walks the installed **providers** in preference order (`SUN`, `SunRsaSign`, `SunEC`, `SunJSSE`, `SunJCE`, ... in a stock JDK; an HSM or a FIPS module can be plugged in as another provider) and returns the first implementation it finds. Algorithm names, modes and paddings are strings, so the API compiles happily with weak choices: `Cipher.getInstance("AES")` silently means ECB. Most real-world crypto bugs are about parameters and composition, not broken primitives.

Each file here is one topic, grouped by directory: `Providers/`, `Digest/`, `Mac/`, `Kdf/`, `Symmetric/`, `Asymmetric/`, `Keys/`, `Certificates/`, `Tls/`, `Randomness/`, and `Misuse/` for the classic mistakes. Every file is a single-file program (`java <File>.java` from its directory) whose header says what the primitive guarantees and what it does not. Keys are generated in-process, so the printed output never shows random bytes: it shows lengths, verification results and tamper detection, and byte values only for published known-answer tests (FIPS, NIST and RFC vectors), which double as a check that the code is right. The certificates and private keys embedded in a few files are throwaway test material created with `keytool`; never embed real keys in source code.

Related notes elsewhere: [`MSC02-J`](../secure-coding/MSC/MSC02_SecureRandom.java) (SecureRandom vs Random), [`MSC03-J`](../secure-coding/MSC/MSC03_HardCodedSecrets.java) (hard-coded secrets), [`SER02-J`](../secure-coding/SER/SER02_SignThenSeal.java) (sign then seal), [`EXP02-J`](../secure-coding/EXP/EXP02_ArraysEquals.java) (comparing arrays and MACs), [EdDSA](../modern-java/java15/EdDsaSignatures.java) (Java 15), [KEM API](../modern-java/java21/KeyEncapsulationMechanism.java) (Java 21) and [deserialization filters](../modern-java/java17/ContextSpecificDeserializationFilters.java) (Java 17).

<!-- INDEX:START -->
- Providers, JCA providers and how `getInstance` picks one: [`Providers/ProviderArchitecture.java`](Providers/ProviderArchitecture.java)
- Providers, listing supported algorithms with `Security.getAlgorithms`: [`Providers/AlgorithmListing.java`](Providers/AlgorithmListing.java)
- Digest, `MessageDigest` and SHA-2, FIPS 180 known-answer tests: [`Digest/Sha2KnownAnswer.java`](Digest/Sha2KnownAnswer.java)
- Digest, why MD5 and SHA-1 are broken: a real MD5 collision: [`Digest/Md5Collision.java`](Digest/Md5Collision.java)
- Digest, length extension: why SHA-256(secret || message) is not a MAC: [`Digest/LengthExtension.java`](Digest/LengthExtension.java)
- Digest, SHA-3 (sponge construction), FIPS 202 known-answer tests: [`Digest/Sha3KnownAnswer.java`](Digest/Sha3KnownAnswer.java)
- Mac, HMAC with RFC 4231 vectors and constant-time verification: [`Mac/HmacKnownAnswer.java`](Mac/HmacKnownAnswer.java)
- Kdf, PBKDF2 parameters and RFC 6070 vectors: [`Kdf/Pbkdf2KnownAnswer.java`](Kdf/Pbkdf2KnownAnswer.java)
- Kdf, storing password hashes: salt, cost and rehash on login: [`Kdf/PasswordStorage.java`](Kdf/PasswordStorage.java)
- Kdf, HKDF implemented over `Mac`, RFC 5869 vectors: [`Kdf/Hkdf.java`](Kdf/Hkdf.java)
- Keys, `KeyGenerator`, `SecretKeySpec` and `SecretKeyFactory`: [`Keys/SecretKeys.java`](Keys/SecretKeys.java)
- Symmetric, AES as a block cipher, FIPS 197 known-answer tests: [`Symmetric/AesKnownAnswer.java`](Symmetric/AesKnownAnswer.java)
- Symmetric, the ECB penguin: repeated blocks survive encryption: [`Symmetric/EcbPenguin.java`](Symmetric/EcbPenguin.java)
- Symmetric, AES-CBC: random IVs, PKCS#7 padding, bit flipping: [`Symmetric/CbcPadding.java`](Symmetric/CbcPadding.java)
- Symmetric, the padding oracle concept, and why AEAD fixes it: [`Symmetric/PaddingOracleConcept.java`](Symmetric/PaddingOracleConcept.java)
- Symmetric, AES-CTR malleability: encryption is not integrity: [`Symmetric/CtrMalleability.java`](Symmetric/CtrMalleability.java)
- Symmetric, AES-GCM with AAD and tamper detection: [`Symmetric/AesGcm.java`](Symmetric/AesGcm.java)
- Symmetric, nonce reuse: XOR of keystreams leaks plaintext: [`Symmetric/NonceReuse.java`](Symmetric/NonceReuse.java)
- Symmetric, ChaCha20-Poly1305 with the RFC 8439 vector: [`Symmetric/ChaCha20Poly1305.java`](Symmetric/ChaCha20Poly1305.java)
- Symmetric, encrypt-then-MAC with AES-CBC and HMAC: [`Symmetric/EncryptThenMac.java`](Symmetric/EncryptThenMac.java)
- Symmetric, AES Key Wrap (`AESWrap`) and envelope encryption: [`Symmetric/AesKeyWrap.java`](Symmetric/AesKeyWrap.java)
- Asymmetric, RSA key generation, key sizes and key structure: [`Asymmetric/RsaKeyGeneration.java`](Asymmetric/RsaKeyGeneration.java)
- Asymmetric, textbook RSA: deterministic and malleable: [`Asymmetric/TextbookRsa.java`](Asymmetric/TextbookRsa.java)
- Asymmetric, RSA-OAEP vs PKCS#1 v1.5, and the OAEP parameter trap: [`Asymmetric/RsaOaep.java`](Asymmetric/RsaOaep.java)
- Asymmetric, RSA signatures: RSASSA-PSS vs PKCS#1 v1.5: [`Asymmetric/RsaPss.java`](Asymmetric/RsaPss.java)
<!-- INDEX:END -->
