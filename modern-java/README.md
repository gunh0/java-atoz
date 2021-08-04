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
<!-- INDEX:END -->
