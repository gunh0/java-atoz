# modern-java

> What each Java release added, one feature per file.

Java has shipped a feature release every six months since Java 9. This section walks through the language and API changes release by release: each file starts with the JEP (or API addition) it covers, explains why the feature exists, and shows the code you had to write before next to the code you can write now. Only final (non-preview) features are used, and every file runs with `java <File>.java` on the release in its directory name or later.

<!-- INDEX:START -->
- Java 9, JEP 269 convenience factory methods for collections: [`java9/CollectionFactories.java`](java9/CollectionFactories.java)
- Java 9, JEP 213 private interface methods: [`java9/PrivateInterfaceMethods.java`](java9/PrivateInterfaceMethods.java)
- Java 10, JEP 286 local-variable type inference (`var`): [`java10/LocalVariableTypeInference.java`](java10/LocalVariableTypeInference.java)
- Java 11, new String methods (`isBlank`, `lines`, `strip`, `repeat`): [`java11/StringMethods.java`](java11/StringMethods.java)
- Java 11, `Files.readString` / `Files.writeString`: [`java11/FilesReadWriteString.java`](java11/FilesReadWriteString.java)
- Java 11, JEP 321 HTTP Client: [`java11/HttpClientExample.java`](java11/HttpClientExample.java)
- Java 12, `Collectors.teeing`: [`java12/CollectorsTeeing.java`](java12/CollectorsTeeing.java)
- Java 14, JEP 361 switch expressions: [`java14/SwitchExpressions.java`](java14/SwitchExpressions.java)
- Java 14, JEP 358 helpful NullPointerExceptions: [`java14/HelpfulNullPointerExceptions.java`](java14/HelpfulNullPointerExceptions.java)
- Java 15, JEP 378 text blocks: [`java15/TextBlocks.java`](java15/TextBlocks.java)
- Java 16, JEP 395 records: [`java16/Records.java`](java16/Records.java)
- Java 16, JEP 394 pattern matching for `instanceof`: [`java16/PatternMatchingInstanceof.java`](java16/PatternMatchingInstanceof.java)
- Java 16, `Stream.toList()` and `Stream.mapMulti()`: [`java16/StreamToListAndMapMulti.java`](java16/StreamToListAndMapMulti.java)
- Java 17, JEP 409 sealed classes: [`java17/SealedClasses.java`](java17/SealedClasses.java)
- Java 9, JEP 213 try-with-resources on effectively final variables: [`java9/TryWithResourcesEffectivelyFinal.java`](java9/TryWithResourcesEffectivelyFinal.java)
- Java 9, `Optional.ifPresentOrElse`, `or` and `stream`: [`java9/OptionalImprovements.java`](java9/OptionalImprovements.java)
- Java 9, `Stream.takeWhile`, `dropWhile`, `iterate` and `ofNullable`: [`java9/StreamTakeWhileDropWhile.java`](java9/StreamTakeWhileDropWhile.java)
- Java 9, JEP 102 Process API updates (`ProcessHandle`): [`java9/ProcessHandleApi.java`](java9/ProcessHandleApi.java)
- Java 9, JEP 259 Stack-Walking API: [`java9/StackWalkerApi.java`](java9/StackWalkerApi.java)
- Java 11, `Predicate.not` and `Optional.isEmpty`: [`java11/PredicateNotOptionalIsEmpty.java`](java11/PredicateNotOptionalIsEmpty.java)
- Java 12, `String.indent` and `String.transform`: [`java12/StringIndentTransform.java`](java12/StringIndentTransform.java)
- Java 12, `Files.mismatch`: [`java12/FilesMismatch.java`](java12/FilesMismatch.java)
<!-- INDEX:END -->
