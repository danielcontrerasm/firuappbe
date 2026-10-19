# FiruApp Backend Study Guide

This guide uses FiruApp/PetTracker examples from this repository. When the repo does not contain a topic directly, the examples are built in the same style as the existing code.

## How To Use This Guide

Study one section at a time:

1. Read the simple concept.
2. Read the FiruApp example.
3. Do the exercises without looking at the answers.
4. Review the answer key.
5. Open the referenced files and explain the code out loud.

Useful files in this project:

- `src/main/java/com/example/pettracker/controller/PetController.java`
- `src/main/java/com/example/pettracker/service/LocationService.java`
- `src/main/java/com/example/pettracker/service/GpsIngestionService.java`
- `src/main/java/com/example/pettracker/security/JwtAuthFilter.java`
- `src/main/java/com/example/pettracker/security/SecurityConfig.java`
- `src/main/java/com/example/pettracker/repository/LocationRepository.java`
- `src/main/java/com/example/pettracker/entity/Pet.java`
- `src/main/java/com/example/pettracker/entity/Location.java`
- `src/main/resources/schema.sql`
- `Dockerfile`
- `docker-compose.yml`

---

# Part 1: Java And Spring Fundamentals

## 1. Java, JDK, SDK, And JVM

Simple concept:

- JDK means Java Development Kit. It includes tools to compile and run Java.
- JVM means Java Virtual Machine. It runs compiled Java bytecode.
- SDK means Software Development Kit. The JDK is a Java SDK.
- FiruApp uses Java 21 in `build.gradle`.

FiruApp example:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

Quiz:

1. Fill in the word: The ______ runs Java bytecode.
2. True or false: The JDK is only needed in production.
3. ABC: Which file declares Java 21?
   - A. `Dockerfile`
   - B. `build.gradle`
   - C. `schema.sql`

Exercise:

Write the command that compiles the project:

```bash
______________
```

Answer:

1. JVM
2. False. The JDK is needed to compile; production can often run with a JRE.
3. B
4. `./gradlew compileJava`

## 2. Files And Strings

Simple concept:

- Files store code, configs, SQL, Docker definitions, and docs.
- Strings are text values.
- Always validate strings from users: empty strings, casing, trimming, and nulls matter.

FiruApp example:

`PetController.applyPetFields(...)` copies request values into an existing pet:

```java
pet.setName(request.name());
pet.setType(request.type());
pet.setRace(request.race());
pet.setImei(request.imei());
pet.setTerminalId(request.terminalId());
```

Clean code note:

If `imei` should never be blank, the controller or service should validate it before saving.

Find the error:

```java
if (request.name().isBlank()) {
    throw new RuntimeException("Name is required");
}
```

Question: What can break here?

Answer:

`request.name()` can be `null`, causing `NullPointerException`.

Better:

```java
if (request.name() == null || request.name().isBlank()) {
    throw new IllegalArgumentException("Name is required");
}
```

## 2A. Core Java Concepts

Simple concept:

Before Spring makes sense, you need the Java basics that Spring code is built from:

- Variables and types.
- Primitive types vs wrapper types.
- Classes and objects.
- Fields, methods, and constructors.
- Access modifiers.
- `static` and `final`.
- Records.
- Enums.
- Interfaces.
- Generics.
- Null handling.
- Exceptions.
- Annotations.
- Packages and imports.
- Equality with `==` vs `.equals(...)`.

### Variables And Types

A variable stores a value. Java is statically typed, so each variable has a declared type.

FiruApp examples:

```java
private Long id;
private String name;
private Integer age;
private Double weight;
private Instant createdAt = Instant.now();
```

Important:

- `String` stores text.
- `Long` stores large whole numbers.
- `Integer` stores whole numbers.
- `Double` stores decimal numbers.
- `Instant` stores a timestamp.

Exercise:

Which type should you use for a pet name?

- A. `String`
- B. `Integer`
- C. `Double`
- D. `Instant`

Answer:

A. `String`

### Primitive Types Vs Wrapper Types

Simple concept:

Primitive types cannot be `null`. Wrapper types can be `null`.

Examples:

```java
private double latitude;
private Double batteryVoltage;
private Integer batteryPercent;
```

In `LocationRequest`, `latitude` and `longitude` are `double`, so they always have a value, even if the client does not send one. `batteryVoltage` and `batteryPercent` are wrappers, so they can be missing.

Common primitive/wrapper pairs:

- `int` / `Integer`
- `long` / `Long`
- `double` / `Double`
- `boolean` / `Boolean`

Find the risk:

```java
private double latitude;
```

Question:

What happens if the request does not send latitude?

Answer:

It may become `0.0`, which can hide a missing input. For required coordinates, validate the request. For optional values, use `Double`.

### Classes And Objects

Simple concept:

A class is a blueprint. An object is a real instance created from that blueprint.

FiruApp example:

```java
public class Pet {
    private Long id;
    private String name;
}
```

`Pet` is the class. A specific pet saved in the database is an object.

Example object idea:

```text
Pet(id=1, name="Milo")
```

Exercise:

In `Pet pet = new Pet();`, what is `pet`?

- A. A class
- B. An object reference
- C. A package
- D. An annotation

Answer:

B. An object reference.

### Fields, Methods, And Constructors

Simple concept:

- A field stores object data.
- A method performs behavior.
- A constructor creates an object.

FiruApp examples:

```java
private String name;
```

This is a field.

```java
public Pet findById(Long id) {
    return petRepository.findById(id).orElse(null);
}
```

This is a method.

Lombok creates constructors here:

```java
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pet {
}
```

Meaning:

- `@NoArgsConstructor`: constructor with no arguments.
- `@AllArgsConstructor`: constructor with all fields.
- `@Builder`: builder-style object creation.

### Access Modifiers

Simple concept:

Access modifiers control where code can be used.

Common modifiers:

- `public`: usable from other packages.
- `private`: usable only inside the same class.
- `protected`: usable by subclasses and same package.
- no modifier: package-private.

FiruApp examples:

```java
public class PetService {
    private final PetRepository petRepository;
}
```

`PetService` is public because Spring and other classes need to use it. `petRepository` is private because only `PetService` should access that field directly.

Exercise:

Which modifier is best for helper methods used only inside one service?

- A. `public`
- B. `private`
- C. `protected`
- D. no modifier

Answer:

B. `private`

### `static` And `final`

Simple concept:

- `static`: belongs to the class, not one object.
- `final`: cannot be reassigned after initialization.

FiruApp example:

```java
private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
```

Meaning:

- `private`: only `PetService` can use it.
- `static`: one shared constant for the class.
- `final`: the value cannot be changed.
- `long`: whole-number type for bytes.

Exercise:

Why is `MAX_IMAGE_SIZE_BYTES` `static final`?

Answer:

It is a constant rule for the class, not data that changes per pet.

### Records

Simple concept:

A record is a compact Java type for immutable data carriers.

FiruApp example:

```java
public record PetDto(
        Long id,
        String name,
        String type,
        String status
) {
}
```

Records automatically provide:

- Constructor.
- Getters named like `id()` and `name()`.
- `equals(...)`.
- `hashCode()`.
- `toString()`.

Why useful for DTOs?

DTOs often just carry API response data and should not contain much behavior.

### Enums

Simple concept:

An enum defines a fixed set of valid values.

FiruApp examples:

```java
public enum Status { ACTIVE, LOST }
```

```java
public enum Role { USER, WALKER, ADMIN }
```

Why useful?

The code cannot accidentally set pet status to `"BROKEN"` or role to `"SUPER_BOSS"` unless those enum values exist.

Exercise:

Which enum value should represent a missing pet?

- A. `ACTIVE`
- B. `LOST`
- C. `ADMIN`
- D. `WALKER`

Answer:

B. `LOST`

### Interfaces

Simple concept:

An interface defines what methods a type must provide.

Spring Data repositories are interfaces:

```java
public interface PetRepository {
    List<Pet> findByOwnerId(Long ownerId);
}
```

In the real project, repository interfaces extend Spring Data interfaces. Spring generates the implementation at runtime.

Why useful?

The service depends on the repository contract, not manual SQL code in the service.

### Generics

Simple concept:

Generics let a class or method work with a specific type safely.

FiruApp examples:

```java
List<Pet>
List<LocationDTO>
Cache<String, CachedPetLookup>
Set<User>
ResponseEntity<PetDto>
```

Read them like this:

- `List<Pet>`: a list containing `Pet` objects.
- `Cache<String, CachedPetLookup>`: a cache where keys are `String` and values are `CachedPetLookup`.
- `ResponseEntity<PetDto>`: an HTTP response whose body is a `PetDto`.

Exercise:

What does `List<LocationDTO>` mean?

Answer:

A list where each item is a `LocationDTO`.

### Null Handling

Simple concept:

`null` means no object is present. Calling a method on `null` causes `NullPointerException`.

Risky:

```java
request.name().isBlank()
```

Safer:

```java
request.name() == null || request.name().isBlank()
```

FiruApp style:

```java
pet == null ? null : pet.getId()
```

This is a ternary expression. It means:

```text
if pet is null, return null; otherwise return pet.getId()
```

### Exceptions

Simple concept:

An exception stops normal flow when something goes wrong.

Example:

```java
throw new IllegalArgumentException("Name is required");
```

Common exception types:

- `IllegalArgumentException`: bad input.
- `RuntimeException`: general unchecked failure.
- `IOException`: file or input/output failure.
- `NullPointerException`: code tried to use `null` like an object.

FiruApp has `ApiExceptionHandler` to convert exceptions into API responses.

Exercise:

What exception type best matches invalid user input?

- A. `IllegalArgumentException`
- B. `IOException`
- C. `InterruptedException`
- D. `NullPointerException`

Answer:

A. `IllegalArgumentException`

### Annotations

Simple concept:

Annotations add metadata that frameworks and tools use.

FiruApp examples:

```java
@Entity
@Service
@RestController
@Autowired
@Transactional(readOnly = true)
@Column(unique = true, nullable = false)
```

Meaning:

- `@Entity`: maps a class to a database table.
- `@Service`: marks business logic class for Spring.
- `@RestController`: marks an HTTP controller.
- `@Autowired`: injects a dependency.
- `@Transactional`: controls database transaction behavior.
- `@Column`: configures a database column.

### Packages And Imports

Simple concept:

A package is the class namespace. Imports let a file use classes from other packages.

FiruApp example:

```java
package com.example.pettracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
```

The package says where the class belongs. The imports bring in JPA annotations and Java time classes.

Exercise:

Which line declares the namespace of a Java file?

- A. `import java.time.Instant;`
- B. `package com.example.pettracker.entity;`
- C. `private String name;`
- D. `@Entity`

Answer:

B.

### Equality: `==` Vs `.equals(...)`

Simple concept:

- `==` compares primitive values or object references.
- `.equals(...)` compares object value if the class implements it properly.

Good FiruApp style for IDs:

```java
pet.getOwner().getId().equals(user.getId())
```

Risk:

This can throw `NullPointerException` if `pet.getOwner()` or `pet.getOwner().getId()` is null.

Safer:

```java
Objects.equals(pet.getOwner().getId(), user.getId())
```

Even safer when owner can be null:

```java
pet.getOwner() != null && Objects.equals(pet.getOwner().getId(), user.getId())
```

Exercise:

Why should you avoid `pet.getOwner().getId() == user.getId()` for `Long` IDs?

Answer:

`Long` is an object wrapper. `==` may compare object references instead of values. Use `.equals(...)` or `Objects.equals(...)`.

### Java Fundamentals Mini Quiz

1. What keyword creates a class?
2. What keyword prevents reassignment?
3. What does `List<Pet>` mean?
4. What happens when code calls a method on `null`?
5. Which is better for fixed values: `String` constants everywhere or an `enum`?

Answers:

1. `class`
2. `final`
3. A list containing `Pet` objects.
4. `NullPointerException`
5. `enum`

### Java Basics Practice Questions

ABCD:

1. Which type should you use for a pet name?
   - A. `String`
   - B. `Integer`
   - C. `Double`
   - D. `Instant`

2. In `Pet pet = new Pet();`, what is `pet`?
   - A. A class
   - B. An object reference
   - C. A package
   - D. An annotation

3. Which modifier is best for helper methods used only inside one service?
   - A. `public`
   - B. `private`
   - C. `protected`
   - D. no modifier

4. Which enum value should represent a missing pet?
   - A. `ACTIVE`
   - B. `LOST`
   - C. `ADMIN`
   - D. `WALKER`

5. Which line declares the namespace of a Java file?
   - A. `import java.time.Instant;`
   - B. `package com.example.pettracker.entity;`
   - C. `private String name;`
   - D. `@Entity`

6. In `private Long id;`, what is `Long`?
   - A. A primitive type.
   - B. A wrapper type that can be `null`.
   - C. A package name.
   - D. A Spring annotation.

7. In `private double latitude;`, what is a risk if the client does not send latitude?
   - A. It may become `0.0` and hide missing input.
   - B. It automatically becomes `null`.
   - C. It becomes a `String`.
   - D. It creates a database index.

8. What does `final` mean for a variable?
   - A. It can be reassigned many times.
   - B. It cannot be reassigned after initialization.
   - C. It becomes public.
   - D. It becomes a controller.

9. What does `static` mean?
   - A. It belongs to the class, not one object instance.
   - B. It belongs only to the database.
   - C. It means the value is always encrypted.
   - D. It means the method is asynchronous.

10. What does a constructor do?
    - A. Creates or initializes an object.
    - B. Deletes a database row.
    - C. Maps an HTTP route.
    - D. Starts Docker.

11. What is a field?
    - A. Data stored on a class or object.
    - B. A Git branch.
    - C. A SQL index only.
    - D. A REST endpoint.

12. What is a method?
    - A. A behavior or function defined in a class.
    - B. A package namespace.
    - C. A Java runtime.
    - D. A database schema.

13. What is an interface?
    - A. A contract that defines methods a type must provide.
    - B. A concrete database table.
    - C. A primitive number.
    - D. A Docker image.

14. What does `List<Pet>` mean?
    - A. A list containing `Pet` objects.
    - B. One `Pet` only.
    - C. A map from pet to user.
    - D. A set of roles.

15. What does `ResponseEntity<PetDto>` mean?
    - A. An HTTP response whose body is a `PetDto`.
    - B. A database entity only.
    - C. A GPS device message.
    - D. A password hash.

16. Which annotation marks a JPA entity?
    - A. `@Entity`
    - B. `@Service`
    - C. `@GetMapping`
    - D. `@Value`

17. Which exception best matches invalid user input?
    - A. `IllegalArgumentException`
    - B. `IOException`
    - C. `InterruptedException`
    - D. `ClassNotFoundException`

18. What does `null` mean?
    - A. No object is present.
    - B. The number zero.
    - C. An empty string.
    - D. A successful HTTP response.

19. Why should `Objects.equals(a, b)` be useful?
    - A. It compares values and handles nulls safely.
    - B. It creates a new database table.
    - C. It starts a thread.
    - D. It converts Java to SQL.

20. What does a Java `record` provide automatically?
    - A. Constructor, accessors, `equals`, `hashCode`, and `toString`.
    - B. A Docker container.
    - C. A Kubernetes pod.
    - D. A JWT secret.

Answers:

1-A, 2-B, 3-B, 4-B, 5-B, 6-B, 7-A, 8-B, 9-A, 10-A, 11-A, 12-A, 13-A, 14-A, 15-A, 16-A, 17-A, 18-A, 19-A, 20-A.

True or false:

1. Primitive types can be `null`.
2. Records automatically provide accessor methods like `id()` and `name()`.
3. Calling a method on `null` causes `NullPointerException`.
4. `final` means a variable cannot be reassigned after initialization.
5. `==` is always the best way to compare `Long` IDs.

Answers:

1. False
2. True
3. True
4. True
5. False

Find the error:

```java
if (request.name().isBlank()) {
    throw new IllegalArgumentException("Name is required");
}
```

Question:

What can break here?

Answer:

`request.name()` can be `null`, causing `NullPointerException`. Check for null before calling `.isBlank()`.

## 2B. Four Fundamental OOP Concepts

The four fundamentals are:

1. Encapsulation
2. Abstraction
3. Inheritance
4. Polymorphism

### Encapsulation

Simple concept:

Encapsulation hides internal state and exposes controlled methods.

FiruApp example:

Entities keep fields private and expose access through methods generated by Lombok `@Data`.

```java
private String terminalId;
```

Service methods control behavior:

```java
pet.setTerminalId(request.terminalId());
```

Better encapsulation example:

```java
public class Pet {
    private String terminalId;

    public void assignTerminalId(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId is required");
        }
        this.terminalId = terminalId.trim();
    }
}
```

Why better?

Validation lives close to the data rule.

### Abstraction

Simple concept:

Abstraction exposes what something does, not all details of how it works.

FiruApp example:

Controller calls:

```java
locationService.getPetRouteBetween(id, from, to)
```

The controller does not need to know query details, parsing details, or mapper details.

### Inheritance

Simple concept:

Inheritance lets a class reuse or specialize parent behavior.

FiruApp uses inheritance less directly in domain classes, but it uses framework inheritance/interfaces through Spring and JPA. For example, repositories extend `JpaRepository`:

```java
public interface PetRepository extends JpaRepository<Pet, Long> {
}
```

This inherits common methods like:

- `findById`
- `findAll`
- `save`
- `deleteById`

### Polymorphism

Simple concept:

Polymorphism lets different implementations be used through one type.

Repository example:

```java
private final PetRepository petRepository;
```

`PetService` uses the `PetRepository` interface, while Spring supplies the runtime implementation.

Quiz:

Match:

1. Hide data and protect invariants.
2. Expose essential behavior and hide details.
3. Reuse parent/interface behavior.
4. Use many implementations through one type.

Choices:

- A. Inheritance
- B. Polymorphism
- C. Encapsulation
- D. Abstraction

Answer:

1-C, 2-D, 3-A, 4-B.

### OOP Java Basics Practice Questions

ABCD:

1. Which OOP concept hides internal state and protects invariants?
   - A. Polymorphism
   - B. Encapsulation
   - C. Inheritance
   - D. Package imports

2. Which OOP concept exposes what something does while hiding how it works?
   - A. Abstraction
   - B. `static`
   - C. `final`
   - D. SQL indexing

3. Which OOP concept lets a class reuse or specialize parent behavior?
   - A. Encapsulation
   - B. Inheritance
   - C. Null handling
   - D. Logging

4. Which OOP concept lets different implementations be used through one interface or parent type?
   - A. Polymorphism
   - B. Primitive typing
   - C. Package-private access
   - D. Constructor overloading only

5. In `PetService`, why is `private final PetRepository petRepository;` better than a public mutable field?
   - A. It hides the dependency field and prevents reassignment.
   - B. It makes SQL run without a database.
   - C. It turns the repository into a controller.
   - D. It disables dependency injection.

6. In `public interface NotificationSender { void send(User user, String message); }`, what does the interface provide?
   - A. A concrete SMS implementation only.
   - B. A contract that multiple notification senders can implement.
   - C. A database table.
   - D. A JWT token.

7. Which relationship is usually stronger?
   - A. Aggregation
   - B. Composition
   - C. Import
   - D. Annotation

8. Which class has better cohesion?
   - A. `PetService` with create, update, and delete pet methods.
   - B. `PetService` with create pet, send email, parse JWT, and optimize SQL methods.
   - C. A controller that directly writes SQL and sends SMS.
   - D. An entity that deploys Docker containers.

Answers:

1-B, 2-A, 3-B, 4-A, 5-A, 6-B, 7-B, 8-A.

True or false:

1. Encapsulation is only about making fields private.
2. A controller should know every SQL detail used by a repository.
3. Constructor injection usually improves testability.
4. High cohesion means a class has one clear responsibility.
5. Loose coupling means classes depend on smaller APIs or abstractions instead of hardcoded concrete implementations.
6. Spring Data repository interfaces are an example of depending on a contract.

Answers:

1. False. Encapsulation also means keeping rules and invariants in the right place.
2. False.
3. True.
4. True.
5. True.
6. True.

Fill the word:

1. Hiding state and protecting rules is ______.
2. Showing only essential behavior is ______.
3. Reusing parent behavior is ______.
4. Using many implementations through one type is ______.
5. Classes with one clear responsibility have high ______.
6. Depending on abstractions helps loose ______.

Answers:

1. Encapsulation
2. Abstraction
3. Inheritance
4. Polymorphism
5. Cohesion
6. Coupling

Find the error:

```java
public class PetService {
    public PetRepository petRepository;
}
```

Question:

What OOP rule is weak here?

Answer:

Encapsulation is weak. The dependency field is public and mutable. Prefer constructor injection with a `private final` field.

Write the whole answer:

Explain why `PetWalkDistanceService` is more cohesive than putting weekly distance calculation directly inside `PetController`.

Expected answer:

`PetWalkDistanceService` has one job: calculating distance summaries. `PetController` should handle HTTP request/response and authorization flow. Keeping distance logic in the service makes the controller smaller, improves testability, and keeps business logic reusable.

## 2C. Java Streams, Optional, And OOP Design

### Stream Intermediate And Terminal Operations

Simple concept:

A Java Stream is a pipeline for processing collections.

Two operation types:

- Intermediate operations transform or filter the stream. They are lazy.
- Terminal operations execute the stream. They produce a result or side effect.

Common intermediate operations:

- `map`
- `filter`
- `flatMap`
- `peek`
- `sorted`
- `distinct`
- `limit`

Common terminal operations:

- `toList`
- `collect`
- `forEach`
- `count`
- `reduce`
- `anyMatch`
- `findFirst`

FiruApp example:

`PetController.list(...)` uses a stream to convert `Pet` entities into `PetDto` responses:

```java
return petService.listForUser(user).stream()
        .map(this::toPetDto)
        .toList();
```

What happens:

1. `stream()` starts the pipeline.
2. `map(this::toPetDto)` is intermediate.
3. `toList()` is terminal and executes the pipeline.

Important:

Without a terminal operation, the stream does nothing.

Find the error:

```java
petService.listForUser(user).stream()
        .map(this::toPetDto);
```

Question:

Why does this not return anything?

Answer:

There is no terminal operation. Add `.toList()`, `.forEach(...)`, `.count()`, etc.

Quiz:

1. Fill in: `map`, `filter`, and `flatMap` are ______ operations.
2. Fill in: `toList`, `count`, and `forEach` are ______ operations.
3. True or false: Intermediate operations execute immediately.

Answer:

1. Intermediate
2. Terminal
3. False

### `map` Vs `flatMap`

Simple concept:

Use `map` when one input becomes one output.

Use `flatMap` when one input becomes many outputs and you want one flat stream.

`map` example:

```java
List<String> petNames = pets.stream()
        .map(Pet::getName)
        .toList();
```

Input:

```text
[Pet("Milo"), Pet("Luna")]
```

Output:

```text
["Milo", "Luna"]
```

FiruApp-style `flatMap` example:

Search groups can have many volunteers. If you have many groups and want one list of all volunteers:

```java
List<Volunteer> volunteers = groups.stream()
        .flatMap(group -> group.getVolunteers().stream())
        .toList();
```

Input shape:

```text
[
  groupA -> [volunteer1, volunteer2],
  groupB -> [volunteer3]
]
```

Output shape:

```text
[volunteer1, volunteer2, volunteer3]
```

Actual project pattern:

`TransitGeofencingService` uses `flatMap` to move from groups to volunteers and then to users.

Find the error:

```java
List<List<Volunteer>> volunteers = groups.stream()
        .map(group -> group.getVolunteers())
        .toList();
```

Question:

Why is this not a flat list?

Answer:

`map` keeps one output per group, so the result is a list of lists. Use `flatMap` to flatten.

Exercise:

Convert this to use `flatMap`:

```java
List<List<Long>> idsByGroup = groups.stream()
        .map(group -> group.getVolunteers().stream()
                .map(Volunteer::getId)
                .toList())
        .toList();
```

Answer:

```java
List<Long> ids = groups.stream()
        .flatMap(group -> group.getVolunteers().stream())
        .map(Volunteer::getId)
        .toList();
```

### The `peek` Operation

Simple concept:

`peek` is an intermediate stream operation mainly intended for debugging.

Example:

```java
List<LocationDTO> dtos = locations.stream()
        .peek(location -> log.debug("Mapping location id={}", location.getId()))
        .map(locationMapper::toDto)
        .toList();
```

Important rules:

- `peek` does not execute without a terminal operation.
- Avoid using `peek` for required business logic.
- Prefer `map` for transformation.
- Prefer `forEach` for intentional side effects.

Bad use:

```java
locations.stream()
        .peek(location -> location.setNeighborhood("Laureles"))
        .map(locationMapper::toDto)
        .toList();
```

Why bad?

It hides mutation inside a stream debugging operation. Someone may remove it thinking it only logs.

Better:

```java
locations.forEach(location -> location.setNeighborhood("Laureles"));
List<LocationDTO> dtos = locations.stream()
        .map(locationMapper::toDto)
        .toList();
```

Find the error:

```java
locations.stream()
        .peek(location -> log.info("location={}", location.getId()));
```

Answer:

No terminal operation, so nothing runs.

Quiz:

True or false: `peek` is a good place for required database saves.

Answer:

False.

### `orElse` Vs `orElseGet`

Simple concept:

`Optional.orElse(value)` always evaluates `value`, even when the Optional has a value.

`Optional.orElseGet(supplier)` only calls the supplier when the Optional is empty.

FiruApp example:

`WalkerMarketplaceService.startWalk(...)` uses `orElseGet` correctly:

```java
Optional<DogWalk> existing = dogWalkRepository.findByWalkRequestId(walkRequestId);
DogWalk dogWalk = existing.orElseGet(() -> DogWalk.builder()
        .walkRequest(walkRequest)
        .owner(walkRequest.getOwner())
        .walkerProfile(walkRequest.getWalkerProfile())
        .pet(walkRequest.getPet())
        .status(DogWalk.Status.IN_PROGRESS)
        .build());
```

Why `orElseGet` is correct:

The builder only runs if there is no existing `DogWalk`.

Bad version:

```java
DogWalk dogWalk = existing.orElse(DogWalk.builder()
        .walkRequest(walkRequest)
        .owner(walkRequest.getOwner())
        .build());
```

Problem:

The new `DogWalk` object is built even if `existing` already has a value. If the fallback saves to DB or calls an API, that side effect would happen unnecessarily.

More dangerous bad version:

```java
Pet pet = petRepository.findByOwnerIdAndNameIgnoreCase(ownerId, name)
        .orElse(petRepository.save(newPet));
```

Problem:

`petRepository.save(newPet)` runs even when the pet already exists.

Correct:

```java
Pet pet = petRepository.findByOwnerIdAndNameIgnoreCase(ownerId, name)
        .orElseGet(() -> petRepository.save(newPet));
```

Quiz:

1. Use ______ when fallback creation is expensive.
2. True or false: `orElseGet` is lazy.
3. True or false: `orElse` is lazy.

Answer:

1. `orElseGet`
2. True
3. False

### Aggregation Vs Composition

Simple concept:

Aggregation and composition describe object relationships.

Aggregation:

- One object uses or references another object.
- The referenced object can live independently.
- Weaker ownership.

Composition:

- One object strongly owns another object.
- The child usually should not exist without the parent.
- Stronger lifecycle relationship.

FiruApp aggregation examples:

`Pet` has an owner:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_id")
private User owner;
```

This is aggregation-like. A `User` can exist without one specific `Pet`.

`DogWalk` has a walker profile, owner, and pet:

```java
private User owner;
private DogWalkerProfile walkerProfile;
private Pet pet;
```

Those referenced objects can exist outside the walk.

FiruApp composition-like example:

`DogWalkPosition` belongs to a `DogWalk`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "dog_walk_id", nullable = false)
private DogWalk dogWalk;
```

A position does not make business sense without its walk. The database relation is `nullable = false`.

Important JPA note:

Composition in OO design is not exactly the same as JPA cascade/orphan removal. If you want database lifecycle ownership, you must configure cascade and orphan removal intentionally.

Quiz:

ABC: Which relationship is stronger?

- A. Aggregation
- B. Composition
- C. Both are always identical

Answer:

B.

Exercise:

Classify:

1. `Pet -> User owner`
2. `DogWalkPosition -> DogWalk`
3. `Location -> Pet`

Possible answer:

1. Aggregation
2. Composition-like
3. Composition-like from business perspective, because a location belongs to a pet; but implementation uses `ManyToOne`.

### Polymorphism

Simple concept:

Polymorphism means different classes can be used through the same interface or parent type.

FiruApp-style example:

Notifications could use polymorphism:

```java
public interface NotificationSender {
    void send(User user, String message);
}

public class EmailNotificationSender implements NotificationSender {
    public void send(User user, String message) {
        // send email
    }
}

public class SmsNotificationSender implements NotificationSender {
    public void send(User user, String message) {
        // send SMS
    }
}
```

Then a service can use a list of senders:

```java
public class NotificationService {
    private final List<NotificationSender> senders;

    public void notifyOwner(User owner, String message) {
        senders.forEach(sender -> sender.send(owner, message));
    }
}
```

Benefit:

`NotificationService` does not need `if email`, `if sms`, `if whatsapp` everywhere. New channels can be added with new implementations.

Project example with framework polymorphism:

Spring injects dependencies by type. `PetService` depends on `PetRepository`, which is an interface. Spring provides an implementation at runtime.

Quiz:

Fill in: Polymorphism lets one ______ have many implementations.

Answer:

Interface or parent type.

### Loose Coupling And High Cohesion

Simple concept:

Loose coupling:

- Classes depend on abstractions or small APIs.
- A change in one class causes fewer changes elsewhere.

High cohesion:

- A class has one clear responsibility.
- Its methods belong together.

FiruApp high cohesion examples:

- `NeighborhoodLookupService`: reverse geocoding.
- `PetWalkDistanceService`: weekly distance calculation.
- `JwtProvider`: JWT token work.
- `LocationRepository`: location database queries.

FiruApp coupling examples:

Good:

```java
public PetService(PetRepository petRepository, GpsIngestionService gpsIngestionService) {
    this.petRepository = petRepository;
    this.gpsIngestionService = gpsIngestionService;
}
```

The service receives dependencies through the constructor. This is easier to test than creating dependencies manually.

Bad:

```java
public class PetService {
    private PetRepository petRepository = new PostgresPetRepository();
}
```

Why bad?

The service is tightly coupled to one concrete repository implementation.

Exercise:

Which class has better cohesion?

A:

```java
class PetService {
    void createPet() {}
    void updatePet() {}
    void deletePet() {}
}
```

B:

```java
class PetService {
    void createPet() {}
    void sendEmail() {}
    void parseJwt() {}
    void optimizeSql() {}
}
```

Answer:

A.

### Encapsulation Practice In FiruApp

Find the weak encapsulation:

```java
pet.setStatus(Pet.Status.valueOf(request.status().toUpperCase()));
```

Question:

What rule is exposed outside `Pet`?

Answer:

The rule for changing status is handled in the controller. If more status rules are added, they may be duplicated.

Better domain method:

```java
public void changeStatus(String status) {
    if (status == null) {
        return;
    }
    this.status = Pet.Status.valueOf(status.toUpperCase());
}
```

Then controller:

```java
pet.changeStatus(request.status());
```

Even better:

Use a service or domain method that validates allowed transitions:

```java
public void markLost() {
    this.status = Status.LOST;
}

public void markFound() {
    this.status = Status.ACTIVE;
}
```

FiruApp already has service methods:

- `PetService.markPetAsLost(...)`
- `PetService.markPetAsFound(...)`

Study note:

Encapsulation is not only `private` fields. It is also keeping business rules in one correct place.

### Mini Exam For Streams And OOP

#### ABC

1. Which operation converts each `Pet` into one `PetDto`?
   - A. `map`
   - B. `flatMap`
   - C. `peek`

2. Which operation is best for debugging a stream value?
   - A. `peek`
   - B. `orElse`
   - C. `@Entity`

3. Which one is lazy?
   - A. `orElse`
   - B. `orElseGet`
   - C. `save`

4. Which OOP concept hides internal state?
   - A. Polymorphism
   - B. Encapsulation
   - C. Inheritance

Answers:

1-A, 2-A, 3-B, 4-B.

#### True Or False

1. `peek` should contain required save logic.
2. `flatMap` can flatten `Stream<List<T>>` into `Stream<T>`.
3. High cohesion means a class does many unrelated jobs.
4. Constructor injection usually improves testability.
5. Composition is usually a stronger ownership relationship than aggregation.

Answers:

1. False
2. True
3. False
4. True
5. True

#### Fill The Word

1. Stream operations like `map` and `filter` are ______ operations.
2. Stream operations like `toList` and `count` are ______ operations.
3. `orElseGet` is lazy because it receives a ______.
4. Classes with one clear responsibility have high ______.
5. Depending on small abstractions helps loose ______.

Answers:

1. Intermediate
2. Terminal
3. Supplier
4. Cohesion
5. Coupling

#### Find The Error

```java
DogWalk dogWalk = existing.orElse(dogWalkRepository.save(newWalk));
```

Answer:

`save(newWalk)` runs even when `existing` has a value. Use `orElseGet(() -> dogWalkRepository.save(newWalk))`.

#### Write The Whole Answer

Explain why `PetWalkDistanceService` is more cohesive than putting weekly distance calculation directly inside `PetController`.

Expected answer:

`PetWalkDistanceService` has one job: calculating distance summaries. `PetController` should handle HTTP request/response and authorization flow. Keeping distance logic in the service makes the controller smaller, improves testability, and keeps business logic reusable.

---

# Part 2: Spring Boot And Microservices

## 3. Spring Boot Layers

Simple concept:

A Spring Boot app often has layers:

- Controller: receives HTTP requests.
- DTO: request/response shape.
- Service: business logic.
- Repository: database access.
- Entity: database model.

FiruApp example:

Pet location flow:

1. `PetController.addLocation(...)`
2. `LocationService.save(...)`
3. `GpsIngestionService.processGpsUpdate(...)`
4. `LocationRepository.save(...)`
5. `LocationDTO` response

Exercise:

Match the layer:

1. `PetController`
2. `LocationRepository`
3. `Location`
4. `LocationDTO`
5. `LocationService`

Choices:

- A. Database table model
- B. HTTP endpoint
- C. Business logic
- D. Database access
- E. API response

Answer:

1-B, 2-D, 3-A, 4-E, 5-C.

## 3A. Spring Boot Application Startup

Simple concept:

Spring Boot starts the app, scans classes, creates objects called beans, wires dependencies, starts web endpoints, and runs background features like scheduled jobs.

FiruApp entry point:

```java
@EnableScheduling
@SpringBootApplication
public class PettrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PettrackerApplication.class, args);
    }
}
```

What this means:

- `@SpringBootApplication` marks the main class and enables auto-configuration.
- `SpringApplication.run(...)` starts the Spring application context.
- `@EnableScheduling` allows methods with `@Scheduled` to run automatically.

Important:

Spring scans from the package of the main class downward. Since the main class is in `com.example.pettracker`, Spring can find classes in packages like:

- `com.example.pettracker.controller`
- `com.example.pettracker.service`
- `com.example.pettracker.repository`
- `com.example.pettracker.security`
- `com.example.pettracker.config`

Exercise:

What would happen if a service class were moved outside `com.example.pettracker` without extra scan configuration?

Answer:

Spring might not find it, so it would not be created as a bean.

## 3B. Beans And Dependency Injection

Simple concept:

A bean is an object managed by Spring. Dependency injection means Spring gives a class the objects it needs instead of the class creating them manually.

FiruApp example:

```java
@Service
public class PetService {
    private final PetRepository petRepository;
    private final GpsIngestionService gpsIngestionService;

    public PetService(PetRepository petRepository, GpsIngestionService gpsIngestionService) {
        this.petRepository = petRepository;
        this.gpsIngestionService = gpsIngestionService;
    }
}
```

What happens:

1. Spring sees `@Service`.
2. Spring creates a `PetService` bean.
3. Spring sees the constructor needs `PetRepository` and `GpsIngestionService`.
4. Spring injects those beans into the constructor.

Why constructor injection is good:

- Required dependencies are clear.
- Fields can be `final`.
- Tests can pass fake dependencies.
- The object cannot be created without what it needs.

Less ideal example from the same class:

```java
@Autowired
private LostPetNotificationService lostPetNotificationService;
```

This works, but constructor injection is usually cleaner because the dependency is visible in the constructor and can be `final`.

Exercise:

Which style is usually preferred for required dependencies?

- A. Constructor injection
- B. Public static fields
- C. Creating dependencies with `new`
- D. Global variables

Answer:

A. Constructor injection.

## 3C. Spring Stereotype Annotations

Simple concept:

Stereotype annotations tell Spring what role a class plays and make it eligible for component scanning.

Common Spring annotations:

- `@RestController`: HTTP controller.
- `@Service`: business logic.
- `@Repository`: database access layer.
- `@Configuration`: bean factory/config class.
- `@Component`: generic Spring-managed class.

FiruApp examples:

```java
@RestController
@RequestMapping("/api/pets")
public class PetController {
}
```

```java
@Service
public class PetService {
}
```

```java
@Configuration
public class GeoConfig {
}
```

Clean architecture rule:

Do not put all logic in controllers. Controllers should parse HTTP inputs, call services, and return HTTP responses. Services should contain most business rules.

Find the smell:

```java
@RestController
public class PetController {
    public void calculateWeeklyDistanceAndSaveAlertsAndSendMessages() {
    }
}
```

Answer:

Too much business logic is being placed in the controller. Move the logic to a service.

## 3D. `@Bean` And Configuration Classes

Simple concept:

Use `@Bean` when you want Spring to manage an object that is created by a method.

FiruApp example:

```java
@Configuration
public class GeoConfig {
    @Bean
    public GeometryFactory geometryFactory() {
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
```

What happens:

- Spring creates one `GeometryFactory` bean.
- Other beans can request `GeometryFactory` in their constructor.
- The configuration is centralized instead of duplicated.

Another FiruApp example:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Why this matters:

`PasswordEncoder` is an interface. The app chooses `BCryptPasswordEncoder` as the implementation and lets Spring inject it wherever needed.

Exercise:

When should you use `@Bean`?

- A. When a config method should create an object managed by Spring.
- B. When a field should be public.
- C. When a DTO should become mutable.
- D. When a controller should return plain text only.

Answer:

A.

## 3E. Controllers, Routes, And Request Data

Simple concept:

Spring MVC maps HTTP requests to Java methods.

FiruApp examples:

```java
@RestController
@RequestMapping("/api/pets")
public class PetController {

```

This means all routes in the controller start with `/api/pets`.

```java
@GetMapping("/{id}")
public ResponseEntity<PetDto> getById(@PathVariable Long id, Authentication authentication) {
}
```

This handles:

```text
GET /api/pets/123
```

Spring binds:

- `@PathVariable Long id`: reads `123` from the URL.
- `Authentication authentication`: comes from Spring Security.

More request annotations:

```java
@RequestBody PetDto request
@RequestParam String from
@RequestPart("pet") PetDto request
@RequestPart(value = "image", required = false) MultipartFile image
```

Meaning:

- `@RequestBody`: JSON body.
- `@RequestParam`: query string like `?from=...`.
- `@RequestPart`: one part of a multipart request.
- `MultipartFile`: uploaded file.

Exercise:

Which annotation reads `{id}` from `/api/pets/{id}`?

- A. `@RequestBody`
- B. `@PathVariable`
- C. `@RequestParam`
- D. `@Service`

Answer:

B. `@PathVariable`.

## 3F. `ResponseEntity` And HTTP Responses

Simple concept:

`ResponseEntity<T>` lets a controller control the HTTP status, headers, and body.

FiruApp examples:

```java
return ResponseEntity.notFound().build();
```

Returns HTTP `404`.

```java
return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
```

Returns HTTP `403`.

```java
return ResponseEntity.status(HttpStatus.CREATED).body(petService.create(pet));
```

Returns HTTP `201` with a body.

```java
return ResponseEntity.ok(toPetDto(pet));
```

Returns HTTP `200` with a body.

Rule of thumb:

- `200 OK`: successful read/update.
- `201 CREATED`: successful create.
- `403 FORBIDDEN`: authenticated but not allowed.
- `404 NOT FOUND`: resource does not exist.

Find the problem:

```java
if (pet == null) {
    return ResponseEntity.ok(null);
}
```

Answer:

For a missing pet, return `404 Not Found`, not `200 OK` with a null body.

## 3G. Properties, Profiles, And `@Value`

Simple concept:

Spring reads configuration from files, environment variables, and command-line arguments. In this project, many settings live in `application.yml`.

FiruApp examples:

```yaml
pettracker:
  time-zone: ${APP_TIME_ZONE:America/Bogota}
```

This means:

- Use environment variable `APP_TIME_ZONE` if it exists.
- Otherwise use `America/Bogota`.

Code example:

```java
public AppTimeService(@Value("${pettracker.time-zone:America/Bogota}") String timeZone) {
}
```

More examples:

```java
@Value("${jwt.secret}")
private String secret;
```

```java
@Value("${gps.ingestion.core-threads:2}")
private int gpsCoreThreads;
```

Why properties matter:

The same code can run locally, in Docker, or in Railway with different database URLs, JWT secrets, CORS origins, and thread pool sizes.

Important rule:

Do not hardcode secrets in code. Use environment variables for production secrets like `JWT_SECRET`.

Exercise:

In `${GPS_INGESTION_CORE_THREADS:2}`, what does `2` mean?

Answer:

It is the default value if `GPS_INGESTION_CORE_THREADS` is not set.

## 3H. Conditional Beans And Scheduled Jobs

Simple concept:

Spring can enable or disable beans based on properties. Spring can also run methods on a schedule.

FiruApp example:

```java
@Service
@ConditionalOnProperty(name = "pettracker.jdbc-watchdog.enabled", havingValue = "true")
public class JdbcConnectionWatchdog {
}
```

This bean is created only when:

```yaml
pettracker:
  jdbc-watchdog:
    enabled: true
```

Scheduled method:

```java
@Scheduled(
        fixedDelayString = "${pettracker.jdbc-watchdog.check-delay-ms:30000}",
        initialDelayString = "${pettracker.jdbc-watchdog.initial-delay-ms:30000}"
)
public void closeStaleIdleTransactions() {
}
```

What this means:

- Wait for the initial delay.
- Run the method.
- After it finishes, wait the fixed delay.
- Run it again.

Important:

Scheduled jobs only run because the main app has `@EnableScheduling`.

Exercise:

Why use `@ConditionalOnProperty` for a watchdog or demo simulator?

Answer:

It lets you turn optional behavior on or off by configuration without changing code.

## 3I. Qualifiers And Multiple Beans Of The Same Type

Simple concept:

Sometimes Spring has multiple beans of the same type. `@Qualifier` tells Spring which one to inject.

FiruApp has several executor beans:

```java
@Bean("alertExecutor")
public Executor alertExecutor() {
}

@Bean("notificationExecutor")
public Executor notificationExecutor() {
}

@Bean("gpsExecutor")
public Executor gpsExecutor() {
}
```

`GpsIngestionService` asks for the GPS executor:

```java
public GpsIngestionService(
        @Qualifier("gpsExecutor") Executor gpsExecutor
) {
}
```

Why this matters:

Without the qualifier, Spring may not know which `Executor` bean to inject.

Exercise:

If a constructor needs the GPS executor, which qualifier should it use?

Answer:

`@Qualifier("gpsExecutor")`

## 3J. Spring Boot Mini Quiz

1. What annotation marks the main Spring Boot app class?
2. What annotation marks a service class?
3. What annotation maps a controller method to `GET`?
4. What does `@Value("${jwt.secret}")` read?
5. Why use `ResponseEntity`?
6. What annotation enables scheduled methods in this app?
7. Why is constructor injection preferred?
8. What does `@Bean` do?

Answers:

1. `@SpringBootApplication`
2. `@Service`
3. `@GetMapping`
4. The `jwt.secret` configuration property.
5. To control HTTP status, headers, and body.
6. `@EnableScheduling`
7. It makes required dependencies explicit and supports `final` fields.
8. It registers an object returned by a method as a Spring-managed bean.

## 4. Microservices

Simple concept:

A microservice is a small, independently deployable service with focused responsibility and clear ownership of its data and APIs.

FiruApp currently looks like a modular monolith: one Spring Boot application with multiple domains inside it.

Current domains:

- Pets
- GPS tracking
- Geofencing
- Walker marketplace
- Transit tracking
- Notifications
- Authentication/security
- Admin metrics

If split later, possible microservices:

- `pet-service`
- `gps-ingestion-service`
- `geofence-service`
- `notification-service`
- `walker-marketplace-service`
- `identity-service`
- `admin-metrics-service`

Important tradeoff:

Microservices add deployment, network, security, observability, and data consistency complexity.

### Monolith Vs Modular Monolith Vs Microservices

Simple concept:

A monolith is one deployable application.

A modular monolith is one deployable application with clean internal boundaries.

Microservices are multiple deployable applications with network boundaries.

FiruApp today:

```text
One Spring Boot app
  -> controllers
  -> services
  -> repositories
  -> one database schema
```

This is not automatically bad. It is often the right starting point.

Better first step:

Make the monolith modular before splitting services.

Example modular boundaries:

```text
pet
gps
geofence
notification
walker
transit
security
```

Good modular monolith rule:

The GPS code should not directly mutate walker marketplace internals. It should call a service method or publish an event.

### When To Split A Microservice

Split only when there is a real reason.

Good reasons:

- One domain needs to scale differently.
- One domain changes much more often than others.
- Different teams own different domains.
- A domain has different reliability requirements.
- A domain needs different storage technology.
- Deploying everything together becomes too risky.

Bad reasons:

- "Microservices are modern."
- "The app has many classes."
- "We want to use Kafka everywhere."
- "We want each table to become a service."

FiruApp example:

`gps-ingestion-service` might be a good future split because GPS traffic can be high-volume and bursty. It may need its own queue, scaling rules, and retry behavior.

`pet-service` might stay with the main API longer because it is normal CRUD and tightly connected to users and permissions.

### Possible FiruApp Service Boundaries

`identity-service`:

- Users.
- Login.
- JWT issuing.
- Roles.
- Password hashing.

Owns data:

- `users`

`pet-service`:

- Pet profiles.
- Owners.
- Pet images.
- Pet status like `ACTIVE` or `LOST`.

Owns data:

- `pet`

`gps-ingestion-service`:

- GPS TCP/Kafka input.
- Terminal ID lookup.
- Location ingestion.
- Location validation.

Owns data:

- raw GPS messages, or writes location events.

`geofence-service`:

- Geofence definitions.
- Geofence event detection.
- Enter/exit alerts.

Owns data:

- `geofence`
- geofence event history.

`notification-service`:

- Email, SMS, WhatsApp, push.
- Notification templates.
- Delivery retries.

Owns data:

- notification logs.
- delivery status.

`walker-marketplace-service`:

- Walker profiles.
- Walk requests.
- Walk messages.
- Dog walks.
- Walk positions.

Owns data:

- walker marketplace tables.

Important:

In microservices, "owns data" usually means other services should not directly write that service's tables.

### Communication Styles

Microservices communicate in two main ways.

Synchronous communication:

```text
pet-service -> identity-service
HTTP request/response
```

Good for:

- Immediate answers.
- User-facing reads.
- Authorization checks.

Risk:

If the called service is slow or down, the caller is affected.

Asynchronous communication:

```text
gps-ingestion-service -> location.created event -> geofence-service
```

Good for:

- Background work.
- High-volume events.
- Decoupling services.
- Retryable workflows.

Risk:

Data becomes eventually consistent. The user may not see every update immediately.

FiruApp example event flow:

```text
GPS device
  -> gps-ingestion-service
  -> LocationRecorded event
  -> geofence-service
  -> GeofenceBreached event
  -> notification-service
  -> SMS/WhatsApp/email
```

### Database Per Service

Simple concept:

A mature microservice should own its database or schema boundary.

Bad microservice pattern:

```text
notification-service directly queries pet table
walker-service directly updates user table
gps-service directly writes geofence table
```

Why bad?

Services become tightly coupled through the database. Changing one schema can break another service.

Better:

```text
notification-service receives an event with the data it needs
```

or:

```text
notification-service calls pet-service API for pet details
```

Tradeoff:

Avoiding shared tables improves ownership, but it makes reporting and joins harder.

### Data Consistency

Simple concept:

Inside one database transaction, consistency is easier. Across microservices, consistency is harder.

Monolith example:

```text
Save location
Check geofence
Create alert
Commit transaction
```

Microservice example:

```text
gps-service saves location
gps-service publishes event
geofence-service consumes event
geofence-service creates geofence event
notification-service sends message
```

This is eventually consistent.

Meaning:

The location may be saved before the alert is sent. If notification fails, the system needs retry logic.

Common patterns:

- Outbox pattern.
- Idempotent consumers.
- Retry with backoff.
- Dead-letter queue.
- Saga for multi-step workflows.

FiruApp example:

If `notification-service` receives the same `GeofenceBreached` event twice, it should avoid sending duplicate alerts. That requires an idempotency key like:

```text
geofenceEventId + notificationType + recipientId
```

### Reliability Basics

Microservices fail in more ways than a monolith.

Possible failures:

- Network timeout.
- Service unavailable.
- Duplicate event.
- Event arrives out of order.
- Database unavailable.
- Message broker unavailable.
- Slow dependency.

Defensive tools:

- Timeouts.
- Retries.
- Circuit breakers.
- Bulkheads.
- Rate limits.
- Idempotency.
- Health checks.

FiruApp example:

If geocoding is slow, GPS ingestion should not block forever. The app should use timeouts and avoid reverse-geocoding every route point.

### API Gateway

Simple concept:

An API gateway is the front door to multiple services.

Possible FiruApp gateway routes:

```text
/api/auth/**      -> identity-service
/api/pets/**      -> pet-service
/api/gps/**       -> gps-ingestion-service
/api/geofences/** -> geofence-service
/api/walkers/**   -> walker-marketplace-service
```

Gateway responsibilities:

- Routing.
- Authentication.
- Rate limiting.
- CORS.
- Request logging.
- TLS termination.

Warning:

Do not put all business logic in the gateway. Keep business rules in the owning service.

### Observability

Simple concept:

When there are many services, logs alone are not enough.

You need:

- Centralized logs.
- Metrics.
- Distributed tracing.
- Correlation IDs.
- Health checks.
- Alerts.

FiruApp example:

A GPS update might pass through:

```text
gps-ingestion-service
geofence-service
notification-service
```

Use a correlation ID so logs from all services can be connected:

```text
X-Correlation-Id: 8f4a...
```

Without this, debugging production issues becomes slow.

### Security Between Services

Simple concept:

User authentication is not enough. Services also need to trust each other.

Common approaches:

- JWT passed from gateway to services.
- Internal service tokens.
- mTLS between services.
- Network policies.
- Least-privilege database users.

FiruApp example:

`notification-service` should not accept a public request that sends arbitrary SMS messages. Only trusted internal services should be able to request notifications.

### Deployment And Scaling

Simple concept:

Each microservice can scale independently.

FiruApp examples:

- Scale `gps-ingestion-service` during high GPS traffic.
- Scale `notification-service` when many alerts are sent.
- Keep `admin-metrics-service` small because it has low traffic.

But each service also needs:

- Build pipeline.
- Deployment config.
- Environment variables.
- Monitoring.
- Logs.
- Rollback strategy.

This is why microservices are not free.

### Practical Evolution Plan For FiruApp

Step 1: Keep one Spring Boot app, but clean boundaries.

- Keep controllers thin.
- Keep business logic in services.
- Avoid one service reaching into another domain's repository directly unless necessary.
- Make DTOs clear.
- Add events inside the monolith where useful.

Step 2: Extract high-pressure domain first.

Best candidate:

```text
gps-ingestion-service
```

Why:

- Different traffic shape.
- Async processing already exists.
- GPS ingestion can use events naturally.
- It can be scaled separately.

Step 3: Extract notifications.

Why:

- Notifications are naturally asynchronous.
- Failures should be retried.
- External providers like Twilio/email can be isolated.

Step 4: Split identity only when needed.

Why:

- Identity affects every service.
- Mistakes create security risk.
- It needs careful token, role, and permission design.

### Microservices Patterns And Best Practices

Simple concept:

Microservices need patterns because normal method calls become network calls. Network calls can be slow, fail, duplicate work, or arrive out of order.

#### Strangler Fig Pattern

Use this pattern to migrate from a monolith to services gradually.

Instead of rewriting everything:

```text
Old FiruApp monolith
  -> keep most features inside
  -> route only GPS ingestion to new gps-ingestion-service
```

Why it helps:

- Lower rewrite risk.
- One domain can move at a time.
- The old app keeps working during migration.

FiruApp example:

Keep pet profile APIs in the monolith, but move GPS TCP/Kafka ingestion into `gps-ingestion-service`.

#### Circuit Breaker Pattern

A circuit breaker stops repeatedly calling a dependency that is already failing.

Example:

```text
notification-service -> SMS provider
```

If the SMS provider times out many times, the circuit opens. The service stops calling it for a short time and returns a controlled failure or queues the message for retry.

Why it helps:

- Prevents slow dependencies from exhausting threads.
- Gives failing systems time to recover.
- Makes failure behavior predictable.

#### Bulkhead Pattern

A bulkhead isolates resources so one failing area does not sink the whole app.

Example:

```text
GPS ingestion thread pool
Notification thread pool
Admin metrics thread pool
```

If notification sending becomes slow, GPS ingestion should still continue.

Best practice:

Do not let one high-volume workflow use every database connection, thread, or queue consumer.

#### Service Discovery

Service discovery helps services find each other without hardcoding host names.

Examples:

```text
geofence-service calls notification-service
api-gateway routes to pet-service
```

Common approaches:

- Kubernetes service names.
- DNS.
- Service registry.
- Environment-based URLs for smaller deployments.

FiruApp practical version:

Start simple with environment variables like:

```text
NOTIFICATION_SERVICE_URL=http://notification-service:8080
```

Move to platform service discovery when the deployment platform supports it.

#### CQRS And Read Models

CQRS means separating writes from reads.

Simple version:

```text
Command side: save GPS location
Query side: read optimized pet route history
```

Why it helps:

- Writes can stay focused on correctness.
- Reads can be optimized for dashboards, maps, and reports.
- Reporting does not need to query every service live.

FiruApp example:

An `admin-metrics-service` might maintain a read model with counts of pets, GPS messages, alerts, and active walks. It can update from events instead of joining every service database.

Warning:

Do not add CQRS just because it sounds advanced. Use it when read needs and write needs are clearly different.

#### API Versioning

Microservices should avoid breaking clients suddenly.

Common options:

```text
/api/v1/pets
/api/v2/pets
```

or:

```text
Accept: application/vnd.firuapp.pet.v1+json
```

FiruApp recommendation:

For a study app or small team, path versioning is easiest to understand:

```text
/api/v1/pets
```

Best practice:

Add new fields without removing old fields. Removing or renaming fields is a breaking change.

#### Contract Testing

Contract testing verifies that services agree on request and response shapes.

Problem:

```text
notification-service expects petName
pet-service renames it to name
```

The code may compile, but production communication can break.

Best practice:

Use tests that check the contract between producer and consumer.

Consumer-driven contract idea:

The consumer describes what it needs. The producer runs tests to prove it still provides that shape.

FiruApp example:

`notification-service` can define the fields it needs from a `GeofenceBreached` event:

```json
{
  "eventId": "abc",
  "petId": 12,
  "petName": "Firulais",
  "recipientId": 99,
  "eventType": "EXIT"
}
```

If `geofence-service` removes `petName`, the contract test should fail before deployment.

#### Blue/Green And Canary Deployments

Blue/green deployment:

```text
Blue = current production
Green = new version
Switch traffic from blue to green
```

Canary deployment:

```text
Send 5% of traffic to the new version
Watch errors and latency
Increase traffic if healthy
```

Why it helps:

- Reduces release risk.
- Makes rollback easier.
- Lets the team detect problems before all users are affected.

FiruApp example:

Deploy a new `gps-ingestion-service` version to a small percentage of GPS device traffic first. If decoding errors rise, roll back before all devices are affected.

#### Backward-Compatible Events

Events are part of your API.

Bad change:

```text
Rename petId to animalId in LocationRecorded
```

This breaks consumers that still expect `petId`.

Better:

```text
Add animalId while keeping petId
Deprecate petId later
Remove only after all consumers migrate
```

Best practices:

- Add fields instead of renaming fields.
- Make consumers ignore unknown fields.
- Include event version when needed.
- Keep event schemas documented.

#### Best Practice Checklist

Before extracting a FiruApp module into a microservice, ask:

1. Does this domain have a clear owner?
2. Can it own its data without direct table sharing?
3. Does it have a stable API or event contract?
4. Does it need independent scaling or deployment?
5. Can failures be retried safely?
6. Are consumers idempotent?
7. Are logs, metrics, traces, and health checks ready?
8. Is there a rollback plan?
9. Are secrets and service-to-service permissions defined?
10. Is the added operational complexity worth it?

If the answer is mostly no, keep it in the modular monolith for now.

### Microservices Mini Quiz

1. What is the biggest operational downside of microservices?
2. Which FiruApp domain is a good first split candidate?
3. Why should services avoid directly writing each other's tables?
4. What is eventual consistency?
5. Why should event consumers be idempotent?
6. What does an API gateway do?
7. Why are correlation IDs useful?
8. Name one bad reason to split into microservices.
9. What does the Strangler Fig pattern help with?
10. Why use a circuit breaker?
11. What is the difference between blue/green and canary deployment?
12. Why are contract tests useful between services?

Answers:

1. More deployment, network, observability, and reliability complexity.
2. `gps-ingestion-service`.
3. It creates tight database coupling.
4. Data becomes consistent after asynchronous work finishes, not immediately in one transaction.
5. Events can be delivered more than once.
6. It routes and protects traffic entering the system.
7. They connect logs across services for one request or workflow.
8. "Microservices are modern" or "the app has many classes."
9. Gradually moving features from a monolith to services without a full rewrite.
10. To stop repeatedly calling a failing or slow dependency.
11. Blue/green switches traffic between two full environments; canary sends a small percentage to the new version first.
12. They catch breaking API or event changes before services are deployed.

Quiz:

True or false: A microservice architecture is always better than a monolith.

Answer:

False. A modular monolith is often simpler until scale or team boundaries require services.

---

# Part 3: API Design And REST

## 5. REST API Basics

Simple concept:

REST uses resources and HTTP methods.

Common methods:

- `GET`: read
- `POST`: create or action
- `PUT`: replace/update
- `PATCH`: partial update
- `DELETE`: delete

FiruApp examples:

- `GET /api/pets`
- `POST /api/pets`
- `PUT /api/pets/{id}`
- `DELETE /api/pets/{id}`
- `POST /api/pets/{id}/locations`
- `GET /api/pets/{id}/route/range?from=...&to=...`

API design note:

`/api/pets/{id}/route/range` is understandable, but `/api/pets/{id}/locations?from=...&to=...` would also be a clean REST design because locations are the resource.

Quiz:

1. Which HTTP method should read a pet route without changing data?
2. Which method should create a new pet?
3. Which method should remove a pet?

Answer:

1. `GET`
2. `POST`
3. `DELETE`

## 6. Idempotent And Non-Idempotent APIs

Simple concept:

Idempotent means calling the same operation multiple times has the same final result.

Examples:

- `GET /api/pets/1` is idempotent.
- `DELETE /api/pets/1` is idempotent in result.
- `PUT /api/pets/1` should be idempotent.
- `POST /api/pets/1/locations` is not idempotent because every call creates a new location.

FiruApp example:

If the same tracker sends the same location twice, FiruApp may store duplicate rows unless an idempotency key is added.

Design improvement:

Add a unique external event ID or `(pet_id, timestamp, latitude, longitude)` duplicate protection.

Exercise:

Write a field name that could make GPS ingestion idempotent:

```text
____________________
```

Possible answer:

`eventId`, `messageId`, `deviceSequence`, or `idempotencyKey`.

## 7. API Optimization

Simple concept:

API optimization means reducing latency, payload size, database load, and repeated work.

FiruApp examples:

- `LocationRepository.findByPetIdOrderByTimestampDesc(..., Pageable)` limits results.
- `LocationService.getPetRouteLast3Hours(...)` limits to `DEFAULT_ROUTE_LIMIT`.
- `@EntityGraph(attributePaths = "pet")` reduces lazy loading queries.
- Reverse geocoding is avoided on `/route` because it could call Nominatim hundreds of times.

Find the performance bug:

```java
public List<LocationDTO> route(Long petId) {
    return locationRepository.findAll()
            .stream()
            .filter(location -> location.getPet().getId().equals(petId))
            .map(locationMapper::toDto)
            .toList();
}
```

Answer:

It loads every location from the database and filters in memory. Use a repository query with `WHERE pet_id = ?` and pagination.

---

# Part 4: DTOs, Entities, JPA, And Spring Data

## 8. Entity Vs DTO

Simple concept:

- Entity maps to database tables.
- DTO maps to API input/output.
- Do not expose entities directly when you need control over fields.

FiruApp example:

`Pet` has database fields like `imageData`. `PetDto` exposes safe API fields like `imageUrl`.

Good:

```java
public record PetDto(
        Long id,
        String name,
        String imei,
        String terminalId
) {}
```

Risky:

```java
@GetMapping("/{id}")
public Pet getPet(@PathVariable Long id) {
    return petService.findById(id);
}
```

Why risky?

It may expose internal fields, lazy-loading problems, and unwanted JSON shape.

## 9. JPA Basics

Simple concept:

JPA maps Java objects to database rows.

FiruApp example:

```java
@Entity
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double latitude;
    private double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;
}
```

Important terms:

- `@Entity`: database table-backed object.
- `@Id`: primary key.
- `@ManyToOne`: many locations belong to one pet.
- `FetchType.LAZY`: do not load relation until needed.

Quiz:

True or false: `@ManyToOne` means one location can belong to many pets.

Answer:

False. Many locations can belong to one pet.

## 10. Spring Data JPA

Simple concept:

Spring Data creates queries from method names or `@Query`.

FiruApp examples:

```java
Optional<Pet> findByTerminalId(String terminalId);
List<Location> findByPetIdOrderByTimestampDesc(Long petId, Pageable pageable);
```

Custom query example:

```java
@Query("SELECT l FROM Location l WHERE l.pet.id = :petId AND l.timestamp BETWEEN :from AND :to ORDER BY l.timestamp ASC")
List<Location> findPetRouteBetween(Long petId, LocalDateTime from, LocalDateTime to, Pageable pageable);
```

Exercise:

Write a Spring Data method name to find pets by owner id:

```java
List<Pet> __________________________(Long ownerId);
```

Answer:

```java
List<Pet> findByOwnerId(Long ownerId);
```

## 11. Transactions

Simple concept:

A transaction groups database operations. If one fails, the transaction can roll back.

FiruApp example:

`LocationService.getNeighborhoodByPetId(...)` is transactional because it may load a location, enrich it, and save new neighborhood fields.

Read-only example:

```java
@Transactional(readOnly = true)
public List<LocationDTO> getPetRouteLast3Hours(Long petId) {
    ...
}
```

Find the error:

```java
@Transactional(readOnly = true)
public void updatePetName(Long id, String name) {
    Pet pet = petRepository.findById(id).orElseThrow();
    pet.setName(name);
}
```

Answer:

The method changes data but is marked `readOnly = true`. Use `@Transactional` without `readOnly`.

---

# Part 5: SQL, Database Optimization, SQL Vs NoSQL

## 12. SQL Basics

Simple concept:

SQL databases store structured data in tables. FiruApp uses PostgreSQL/PostGIS.

FiruApp schema examples:

```sql
CREATE INDEX IF NOT EXISTS idx_location_pet_timestamp ON location(pet_id, timestamp DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pet_terminal_id ON pet(terminal_id) WHERE terminal_id IS NOT NULL;
```

Why indexes matter:

`GET /api/pets/{id}/route` filters by `pet_id` and `timestamp`. The index helps the database find rows faster.

Exercise:

Write SQL to select locations for pet 1 between two timestamps:

```sql
SELECT *
FROM location
WHERE pet_id = __
  AND timestamp BETWEEN ______ AND ______
ORDER BY timestamp ASC;
```

Answer:

```sql
SELECT *
FROM location
WHERE pet_id = 1
  AND timestamp BETWEEN '2026-10-06 07:00:00' AND '2026-10-06 10:00:00'
ORDER BY timestamp ASC;
```

## 13. SQL Optimization

Simple concept:

Make queries fast by filtering in SQL, indexing important columns, selecting only needed rows, and avoiding N+1 queries.

FiruApp examples:

- Good: `WHERE l.pet.id = :petId`
- Good: `PageRequest.of(0, DEFAULT_ROUTE_LIMIT)`
- Good: `@EntityGraph(attributePaths = "pet")`
- Good: indexes in `schema.sql`

Bad:

```java
locationRepository.findAll().stream().filter(...)
```

SQL optimization checklist:

- Is there a `WHERE` clause?
- Is there a useful index?
- Is there a limit/page?
- Are joins necessary?
- Are you selecting too many columns?
- Is the query executed inside a loop?

## 14. SQL Vs NoSQL

Simple concept:

SQL:

- Tables and relations.
- Strong consistency.
- Good for transactions and structured queries.

NoSQL:

- Document/key-value/wide-column/graph models.
- Flexible schemas.
- Often easier to scale horizontally for specific patterns.

FiruApp fit:

PostgreSQL is a good fit for pets, users, walks, geofences, and historical locations. Redis could be useful for caching, sessions, rate limiting, or short-lived GPS state.

Quiz:

ABC: Which database model is best for relational entities like users, pets, walks, and locations?

- A. SQL
- B. NoSQL only
- C. Plain text files

Answer:

A.

---

# Part 6: Security, Spring Security, JWT, Web Security

## 15. Spring Security

Simple concept:

Spring Security protects endpoints by authenticating users and checking roles/permissions.

FiruApp example files:

- `SecurityConfig.java`
- `JwtAuthFilter.java`
- `JwtProvider.java`
- `CustomUserDetailsService.java`

Common flow:

1. User logs in.
2. API returns JWT.
3. Frontend sends `Authorization: Bearer <token>`.
4. `JwtAuthFilter` validates token.
5. Controller gets `Authentication`.

## 16. JWT

Simple concept:

JWT is a signed token containing claims like username/email and expiration.

Good practices:

- Use a strong secret.
- Set expiration.
- Never store passwords in JWT.
- Validate signature and expiration.
- Send over HTTPS.

FiruApp example:

`README.md` says Railway must set:

```env
JWT_SECRET=generate-a-long-random-secret
```

Quiz:

True or false: A JWT should contain the user's raw password.

Answer:

False.

## 17. Web Security

Simple concept:

Web security protects against attacks like:

- Broken authentication
- Broken authorization
- SQL injection
- XSS
- CSRF
- Sensitive data exposure
- Rate-limit abuse

FiruApp examples:

- `canAccessPet(...)` prevents owners from reading other owners' pets.
- Repositories use parameters, not string-concatenated SQL.
- `PetDto` avoids exposing raw image bytes.

Find the security bug:

```java
@GetMapping("/{id}/locations")
public List<LocationDTO> locations(@PathVariable Long id) {
    return locationService.getByPetId(id);
}
```

Answer:

There is no authentication/authorization check. A user could read another user's pet locations.

Better:

Check current user and call `canAccessPet(...)`.

---

# Part 7: Exceptions, Logging, Monitoring, Observability

## 18. Exceptions

Simple concept:

Exceptions represent unexpected or invalid conditions.

Bad:

```java
throw new RuntimeException("Pet not found");
```

Better:

```java
throw new NotFoundException("Pet not found");
```

Then map it in `ApiExceptionHandler` to `404`.

FiruApp current style:

Several services throw `RuntimeException`. `ApiExceptionHandler` centralizes API errors.

Exercise:

Create three custom exceptions:

```java
class NotFoundException extends RuntimeException {}
class ForbiddenException extends RuntimeException {}
class BadRequestException extends RuntimeException {}
```

Then map:

- `NotFoundException` -> 404
- `ForbiddenException` -> 403
- `BadRequestException` -> 400

## 19. Logging

Simple concept:

Logging records events. Good logs help debug production.

FiruApp example:

`GpsIngestionService` uses `@Slf4j` and logs unknown terminal IDs and queue drops.

Good log:

```java
log.warn("GPS location for unknown terminalId={} lat={} lon={}", terminalId, latitude, longitude);
```

Bad log:

```java
log.warn("bad gps");
```

Why bad?

It lacks context.

## 20. Metrics And Observability

Simple concept:

Observability means understanding system health using:

- Logs
- Metrics
- Traces
- Health checks

FiruApp examples:

- `AdminMetricsController`
- `AdminMetricsService`
- `HttpRequestMetricsService`
- `JdbcConnectionWatchdog`
- GPS dropped tasks metric in `GpsIngestionService`

Useful metrics:

- Request count
- Error count
- Latency
- DB connection pool usage
- GPS queue drops
- Login failures
- Notification failures

Quiz:

Fill in: Logs tell you what happened. Metrics tell you ______ often or ______ much.

Answer:

How often or how much.

---

# Part 8: Threads, Concurrency, Sync, Async, Reactive

## 21. Threads And Concurrency

Simple concept:

A thread is one path of execution. Concurrency means multiple tasks can make progress over the same time period.

FiruApp example:

GPS ingestion uses an executor:

```java
@Qualifier("gpsExecutor") Executor gpsExecutor
```

The service submits GPS processing work asynchronously:

```java
gpsExecutor.execute(() -> {
    task.run();
});
```

Why?

GPS TCP handling should not block for slow database work.

## 22. Thread Safety

Simple concept:

Shared mutable state must be protected.

FiruApp examples:

```java
private final Cache<String, CachedPetLookup> petByTerminalIdCache;
private final AtomicLong droppedTasks = new AtomicLong();
```

Why Caffeine `Cache`?

Multiple GPS tasks can read/write the cache safely, and the cache also handles expiration and maximum size.

Why `AtomicLong`?

Multiple threads can increment `droppedTasks` safely.

Find the concurrency bug:

```java
private long droppedTasks = 0;

public void drop() {
    droppedTasks++;
}
```

Answer:

`droppedTasks++` is not atomic. Use `AtomicLong`.

## 23. Sync Vs Async

Simple concept:

Synchronous:

- Caller waits for result.
- Easier to reason about.

Asynchronous:

- Caller does not wait.
- Better for background work.
- Harder error handling.

FiruApp examples:

- Manual location `POST /api/pets/{id}/locations` is synchronous and returns saved data.
- GPS decoded locations are processed asynchronously by `GpsIngestionService`.

Quiz:

True or false: Async code always makes a system faster.

Answer:

False. It can improve throughput, but adds overhead and complexity.

## 24. Reactive And Mono

Simple concept:

Reactive programming handles asynchronous streams without blocking threads. In Spring WebFlux, `Mono<T>` means zero or one async value.

FiruApp status:

FiruApp uses Spring MVC, not WebFlux. Controllers return `ResponseEntity<T>` and `List<T>`, not `Mono<T>`.

MVC example:

```java
public ResponseEntity<PetDto> getById(Long id) {
    return ResponseEntity.ok(dto);
}
```

Reactive style example:

```java
public Mono<PetDto> getById(Long id) {
    return petService.findById(id);
}
```

When to use reactive:

- Many concurrent I/O operations.
- Streaming.
- Non-blocking database/client stack.

When not to use it:

- Simple CRUD with blocking JPA.

---

# Part 9: Collections, Algorithms, Data Structures

## 25. Collections

Simple concept:

Common Java collections:

- `List`: ordered items.
- `Map`: key-value lookup.
- `Set`: unique items.
- `Queue`: process items in order.

FiruApp examples:

- `List<LocationDTO>` for routes.
- `Cache<String, CachedPetLookup>` for terminal ID cache.
- `Set<String>` for allowed image MIME types.

Exercise:

Choose the best collection:

1. Store unique allowed image types.
2. Store route positions in order.
3. Cache terminal ID to pet lookup.

Answer:

1. `Set`
2. `List`
3. `Map`

### Collections Practice Questions

ABCD:

1. In `public List<Pet> listForUser(User user)`, why is `List<Pet>` the right return type?
   - A. The method returns pets in a collection that can contain multiple results.
   - B. The method returns only one pet.
   - C. The method stores key-value pairs.
   - D. The method guarantees unique image MIME types.

2. In `List<LocationDTO>` for pet routes, which collection feature matters most?
   - A. Unique values only.
   - B. Key-value lookup by terminal ID.
   - C. Ordered items.
   - D. Thread-safe writes.

3. In `private final Cache<String, CachedPetLookup> petByTerminalIdCache;`, what is the key?
   - A. `CachedPetLookup`
   - B. `String`
   - C. `Long`
   - D. `LocationDTO`

4. Why is Caffeine better than a raw `ConcurrentHashMap` for `petByTerminalIdCache`?
   - A. It is thread-safe and also supports expiration and maximum size.
   - B. It sorts pets by name automatically.
   - C. It stores duplicate terminal IDs.
   - D. It makes database queries faster.

5. In `private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(...)`, why is `Set` a good choice?
   - A. It allows duplicate MIME types.
   - B. It stores values in timestamp order.
   - C. It stores unique allowed MIME types.
   - D. It maps MIME types to pets.

6. Which literal example creates the allowed image type collection in `PetService`?
   - A. `List.of("image/jpeg", "image/png")`
   - B. `Map.of("image/jpeg", "image/png")`
   - C. `Set.of("image/jpeg", "image/png", "image/webp", "image/gif")`
   - D. `new ArrayList<>("image/jpeg")`

7. In `config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));`, what does `List.of(...)` create?
   - A. A map from HTTP method to URL.
   - B. A list of allowed HTTP methods.
   - C. A set of image MIME types.
   - D. A database table.

8. In `Map.of("error", exception.getMessage())`, why is `Map` the best match?
   - A. It returns one key-value pair for the JSON response.
   - B. It keeps route positions in order.
   - C. It removes duplicate volunteers.
   - D. It runs code asynchronously.

9. In `List<List<Double>> coordinates`, what does the nested `List` represent?
   - A. A key-value cache.
   - B. A list of coordinate pairs, where each pair is also a list.
   - C. A set of unique MIME types.
   - D. A single pet owner.

10. Which collection best matches `Set<User> volunteers = new HashSet<>();` in `SearchGroup`?
    - A. `List`, because volunteers must be stored by index only.
    - B. `Map`, because each volunteer must be stored with a string key.
    - C. `Set`, because the same volunteer should not appear twice.
    - D. `Queue`, because volunteers must be processed first-in first-out.

11. In `if (walkRequests.isEmpty()) { return List.of(); }`, why return `List.of()`?
    - A. It returns an empty list without creating mutable data.
    - B. It returns `null`.
    - C. It creates a map with one empty key.
    - D. It starts a background GPS task.

12. In `List<Long> walkRequestIds = walkRequests.stream().map(WalkRequest::getId).toList();`, what is the final result?
    - A. A `Set<Long>` of unique owners.
    - B. A `Map<Long, WalkRequest>`.
    - C. A `List<Long>` containing walk request IDs.
    - D. A single `Long`.

13. In `Map<Long, Long> activeDogWalkIdsByRequestId`, what does the map connect?
    - A. Walk request id to active dog walk id.
    - B. Pet name to image MIME type.
    - C. HTTP method to controller name.
    - D. Volunteer id to city name.

14. In `.collect(Collectors.toMap(dogWalk -> dogWalk.getWalkRequest().getId(), DogWalk::getId, (first, second) -> first))`, why use `Collectors.toMap(...)`?
    - A. To convert a stream of `DogWalk` objects into a lookup map.
    - B. To convert a map into a stream.
    - C. To sort all dog walks by timestamp.
    - D. To remove all walk requests.

15. In `(first, second) -> first` inside `Collectors.toMap(...)`, what is this function for?
    - A. Handling duplicate keys.
    - B. Creating a `List`.
    - C. Reading an HTTP request body.
    - D. Saving an entity.

16. In `.map(position -> Map.of(dogWalk.getId(), position)).orElseGet(Map::of)`, what does `Map::of` provide when no position exists?
    - A. An empty map.
    - B. A list with one position.
    - C. A set with one dog walk.
    - D. A null pointer.

17. In `Map<Long, DogWalkPosition> latestPositionsByWalkId`, why is `Map` useful?
    - A. It allows direct lookup of latest position by dog walk id.
    - B. It stores positions only in insertion order.
    - C. It guarantees each position has a different timestamp.
    - D. It converts positions to JSON automatically.

18. In `Set<User> volunteers = searchGroup.getVolunteers();`, what should the code do before streaming if the set might be null?
    - A. Check for null and use an empty collection when missing.
    - B. Convert it to a `String`.
    - C. Save it to the database.
    - D. Change it to `Map.of()`.

19. In `volunteers.stream().map(SearchGroupMapper::toVolunteerMemberDto).toList()`, why use `map`?
    - A. To transform each `User` into a `VolunteerMemberDto`.
    - B. To flatten nested lists.
    - C. To create a thread-safe cache.
    - D. To delete duplicate volunteers from the database.

20. In `private static List<Double> toLatLng(Coordinate coordinate) { return List.of(coordinate.y, coordinate.x); }`, what does the returned `List<Double>` contain?
    - A. Latitude and longitude values.
    - B. A pet id and owner id.
    - C. Two HTTP methods.
    - D. Two image MIME types.

Answers:

1-A, 2-C, 3-B, 4-A, 5-C, 6-C, 7-B, 8-A, 9-B, 10-C, 11-A, 12-C, 13-A, 14-A, 15-A, 16-A, 17-A, 18-A, 19-A, 20-A.

Find the error:

```java
private HashMap<String, CachedPetLookup> cache = new HashMap<>();
```

Question:

Why is this risky in GPS ingestion?

Answer:

GPS ingestion is concurrent, so `HashMap` is unsafe. For this project, use the Caffeine cache bean so the cache is thread-safe and has expiration and maximum-size policy.

## 26. Algorithms

Simple concept:

An algorithm is a step-by-step solution.

FiruApp example: weekly pet distance.

Algorithm:

1. Get all walk positions for a pet during a week.
2. Sort by walk id and time.
3. For each consecutive pair in the same walk, calculate distance.
4. Add distance to the day total.
5. Return 7 rows.

Find the bug:

```java
for (int i = 1; i < positions.size(); i++) {
    total += distance(positions.get(i - 1), positions.get(i));
}
```

Bug:

It may connect the last point of one walk to the first point of another walk.

Fix:

Check that both positions have the same `dogWalk.id`.

---

# Part 10: SOLID, Clean Code, Design Patterns

## 27. SOLID

Simple concept:

- S: Single Responsibility Principle
- O: Open/Closed Principle
- L: Liskov Substitution Principle
- I: Interface Segregation Principle
- D: Dependency Inversion Principle

FiruApp examples:

Single Responsibility:

- `NeighborhoodLookupService` resolves geocoding.
- `LocationService` coordinates location operations.
- `PetWalkDistanceService` calculates distance summaries.

Dependency Inversion:

Services depend on repository interfaces, not SQL code directly.

Find the SOLID issue:

```java
public class PetController {
    public PetDto createPet(...) {
        // validate auth
        // save pet
        // send SMS
        // call geocoder
        // calculate weekly distance
        // write SQL manually
    }
}
```

Answer:

The controller has too many responsibilities. Move business logic to services.

## 28. Clean Code

Simple rules:

- Use clear names.
- Keep methods small.
- Validate inputs at boundaries.
- Avoid duplicate logic.
- Avoid hidden side effects.
- Prefer DTOs for API shape.
- Keep controllers thin.

FiruApp good example:

`LocationService.parseRequestTimestamp(...)` centralizes timestamp parsing.

FiruApp improvement opportunity:

Replace generic `RuntimeException` with domain exceptions.

## 29. Design Patterns

Patterns visible or useful in FiruApp:

Repository pattern:

- `PetRepository`
- `LocationRepository`

DTO pattern:

- `PetDto`
- `LocationDTO`

Service layer pattern:

- `PetService`
- `LocationService`

Mapper pattern:

- `PetMapper`
- `LocationMapper`

Factory/builder pattern:

```java
Location.builder()
        .pet(pet)
        .latitude(request.getLatitude())
        .longitude(request.getLongitude())
        .build();
```

Strategy pattern example for future notifications:

```java
interface NotificationSender {
    void send(User user, String message);
}

class EmailNotificationSender implements NotificationSender {}
class SmsNotificationSender implements NotificationSender {}
class WhatsappNotificationSender implements NotificationSender {}
```

---

# Part 11: Clean Architecture, DDD, Hexagonal Architecture

## 30. Clean Architecture

Simple concept:

Business rules should not depend directly on frameworks.

Typical layers:

- Domain
- Application/use cases
- Infrastructure
- Interface adapters/controllers

FiruApp current structure:

FiruApp uses a common Spring layered architecture. It is not strict Clean Architecture, but it has clear controllers, services, repositories, entities, and DTOs.

Clean Architecture version example:

```text
domain/
  Pet.java
  Location.java
application/
  SavePetLocationUseCase.java
ports/
  LocationRepositoryPort.java
infrastructure/
  JpaLocationRepositoryAdapter.java
web/
  PetController.java
```

## 31. DDD

Simple concept:

Domain Driven Design models the business language and rules.

FiruApp bounded contexts:

- Pet Tracking
- Walker Marketplace
- Search Groups
- Notifications
- Transit Tracking

Domain words:

- Pet
- Location
- Geofence
- DogWalk
- WalkRequest
- DogWalkerProfile
- Alert

DDD exercise:

Name one invariant for `DogWalk`.

Possible answer:

A dog walk should not accept positions after it is completed.

FiruApp already enforces this in `addWalkPosition(...)` by checking `dogWalk.getStatus() == IN_PROGRESS`.

## 32. Hexagonal Architecture

Simple concept:

Hexagonal architecture separates core logic from external systems through ports and adapters.

FiruApp example:

`NeighborhoodLookupService` currently calls Nominatim directly. In hexagonal style:

```java
interface ReverseGeocoder {
    PetNeighborhoodDto resolve(Location location);
}
```

Adapter:

```java
class NominatimReverseGeocoder implements ReverseGeocoder {}
```

Benefit:

Tests can use a fake geocoder without HTTP calls.

---

# Part 12: Event Driven Architecture, Batch, Caching

## 33. Event Driven Architecture

Simple concept:

Services communicate through events instead of direct calls.

FiruApp hints:

- `GpsKafkaProducer`
- `GpsKafkaConsumer`
- GPS ingestion is naturally event-driven.

Example event:

```json
{
  "type": "PET_LOCATION_RECORDED",
  "petId": 1,
  "latitude": 6.2442,
  "longitude": -75.5812,
  "timestamp": "2026-10-06T12:00:00Z"
}
```

Possible subscribers:

- Geofencing service
- Notification service
- Analytics service
- Search group service

Quiz:

True or false: Event-driven systems make debugging easier by default.

Answer:

False. They need correlation IDs, logs, retries, and dead-letter queues.

## 34. Spring Batch

Simple concept:

Spring Batch processes large jobs in steps. It is useful for imports, reports, cleanup, and scheduled aggregation.

FiruApp does not currently use Spring Batch.

Possible FiruApp batch jobs:

- Backfill `location.neighborhood` for old rows.
- Generate daily distance summaries.
- Clean old GPS raw messages.
- Send weekly owner reports.

Batch vocabulary:

- Job: whole batch process.
- Step: one part of a job.
- Reader: reads records.
- Processor: transforms records.
- Writer: saves results.

## 35. Caching

Simple concept:

Caching stores expensive results temporarily.

FiruApp example:

`GpsIngestionService` caches terminal ID to pet lookup:

```java
private final Cache<String, CachedPetLookup> petByTerminalIdCache;
```

Why cache?

GPS devices send frequent messages. Looking up the pet in DB every time can be expensive.

Current implementation:

```java
@Bean("gpsPetByTerminalIdCache")
public Cache<String, CachedPetLookup> gpsPetByTerminalIdCache(...) {
    return Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfter(...)
            .recordStats()
            .build();
}
```

Current TTL configuration:

```yaml
gps:
  cache:
    pet-by-terminal-id:
      maximum-size: 10000
      known-ttl: PT24H
      unknown-ttl: PT1M
```

Strategy:

- Known terminal IDs are cached for 24 hours by default because a terminal ID usually belongs to the same physical GPS device for a long time.
- Unknown terminal IDs are cached for only 1 minute because a tracker may start sending data before the pet/device registration is completed.
- Pet create/update/delete paths still manually invalidate the terminal ID cache, so normal application changes do not need to wait for TTL expiry.

Why Caffeine is better than a raw `ConcurrentHashMap` here:

- It is still thread-safe.
- It can expire entries automatically.
- It can limit memory with `maximumSize`.
- It keeps cache policy in one bean instead of scattering manual expiry checks in service logic.
- It supports cache stats for future metrics.
- It avoids keeping stale unknown terminal IDs forever.

What `ConcurrentHashMap` gives you:

- Safe concurrent reads and writes.

What `ConcurrentHashMap` does not give you by itself:

- TTL expiration.
- Maximum size eviction.
- Cache statistics.
- A clear cache policy.

Cache risks:

- Stale values
- Memory growth
- Invalidation bugs

Quiz:

Fill in: Cache invalidation is needed when the underlying ______ changes.

Answer:

Data.

---

# Part 13: Docker, Kubernetes, Load Balancing, API Gateway

## 36. Docker

Simple concept:

Docker packages an app and its runtime into an image.

FiruApp Dockerfile:

- Build stage uses JDK.
- Runtime stage uses JRE.
- Runs `java -jar app.jar`.

Command examples:

```bash
docker build -t firuapp-backend .
docker run -p 8080:8080 firuapp-backend
```

## 37. Docker Compose

Simple concept:

Docker Compose runs multiple services together.

FiruApp compose file includes:

- PostgreSQL/PostGIS
- Redis

Command:

```bash
docker compose up
```

## 38. Kubernetes

Simple concept:

Kubernetes runs containers in a cluster.

Common objects:

- Pod: one or more containers.
- Deployment: manages replicas.
- Service: stable network access to pods.
- ConfigMap: config.
- Secret: sensitive config.
- Ingress: external HTTP entry.

FiruApp deployment example:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: firuapp-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: firuapp-backend
  template:
    metadata:
      labels:
        app: firuapp-backend
    spec:
      containers:
        - name: app
          image: firuapp-backend:latest
          ports:
            - containerPort: 8080
```

## 39. Load Balancing

Simple concept:

A load balancer distributes traffic across multiple app instances.

Requirements:

- App should be stateless.
- Sessions should not live only in one server memory.
- Shared data goes to DB/Redis/object storage.

FiruApp:

JWT helps stateless auth because each request carries its auth token.

## 40. API Gateway

Simple concept:

An API gateway is the front door for APIs.

Responsibilities:

- Routing
- TLS termination
- Authentication
- Rate limiting
- Request logging
- Versioning

FiruApp future:

If split into services, an API gateway could route:

- `/api/pets/**` -> pet service
- `/api/dog-walks/**` -> walker service
- `/api/auth/**` -> identity service

---

# Part 14: Stateless Vs Stateful

## 41. Stateless

Simple concept:

A stateless server does not need local memory from a previous request to handle the next request.

FiruApp examples:

- JWT auth is stateless.
- Controllers read auth from token and data from DB.

## 42. Stateful

Simple concept:

Stateful systems remember session/process state.

FiruApp examples:

- Database is stateful.
- GPS TCP connections are stateful while connected.
- In-memory GPS cache is stateful but disposable.

Quiz:

True or false: A stateless app can still use a database.

Answer:

True. Stateless means app server instances do not depend on local request history.

---

# Part 15: Git And Linux

## 43. Git

Simple concept:

Git tracks source code history.

Common commands:

```bash
git status
git diff
git add src/main/java/com/example/pettracker/service/LocationService.java
git commit -m "FIRUAPP-001 Add pet route time range query"
git log --oneline -5
```

Good commit message format:

```text
FIRUAPP-001 Add weekly pet walk distance table
```

Exercise:

Write a commit message for fixing a null Boolean bug in `LocationMapper`.

Answer:

```text
FIRUAPP-001 Fix null neighborhood resolved mapping
```

## 44. Linux

Simple concept:

Linux commands help inspect and operate apps.

Useful commands:

```bash
pwd
ls -la
find . -name "*.java"
grep -R "terminalId" -n src/main/java
tail -f app.log
ps aux | grep java
curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/pets
```

Exercise:

Find all Java files containing `JwtProvider`:

```bash
grep -R "JwtProvider" -n src/main/java
```

---

# Part 16: Testing And Quality

## 45. Testing

Simple concept:

Tests prove behavior and protect changes.

Types:

- Unit test: small class/method.
- Integration test: Spring + DB/API pieces.
- End-to-end test: full user flow.

FiruApp examples:

- `GpsMessageHandlerTest`
- `V41ProtocolDecoderTest`
- `LocationServiceTest`

Unit test example:

```java
@Test
void parsesUtcTimestampInAppTimezone() {
    LocalDateTime timestamp = locationService.parseRequestTimestamp("2026-10-06T12:00:00Z");
    assertThat(timestamp).isEqualTo(LocalDateTime.of(2026, 10, 6, 7, 0));
}
```

Quality checklist:

- Does it compile?
- Are tests passing?
- Are errors handled?
- Are permissions checked?
- Are queries indexed?
- Are logs useful?
- Are DTOs backward compatible?

## 46. Debugging

Simple concept:

Debugging is finding why actual behavior differs from expected behavior.

Process:

1. Reproduce the bug.
2. Read the exact error.
3. Locate the failing code.
4. Create a minimal fix.
5. Add a test if possible.
6. Verify.

FiruApp example:

Bug:

```text
Cannot invoke "java.lang.Boolean.booleanValue()" because getNeighborhoodResolved() is null
```

Cause:

Java unboxed a nullable `Boolean` because a ternary mixed `Boolean` and primitive `boolean`.

Fix:

```java
neighborhood == null
        ? location.getNeighborhoodResolved()
        : Boolean.valueOf(neighborhood.resolved())
```

---

# Part 17: API Calls

## 47. Calling FiruApp APIs

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@example.com","password":"secret"}'
```

List pets:

```bash
curl http://localhost:8080/api/pets \
  -H "Authorization: Bearer TOKEN"
```

Create location:

```bash
curl -X POST http://localhost:8080/api/pets/1/locations \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 6.2442,
    "longitude": -75.5812,
    "batteryPercent": 82,
    "timestamp": "2026-10-06T12:00:00Z"
  }'
```

Route range:

```bash
curl "http://localhost:8080/api/pets/1/route/range?from=2026-10-06T12:00:00Z&to=2026-10-06T15:00:00Z" \
  -H "Authorization: Bearer TOKEN"
```

Weekly distance:

```bash
curl "http://localhost:8080/api/pets/1/walk-distance/week?weekStart=2026-10-05" \
  -H "Authorization: Bearer TOKEN"
```

---

# Full Practice Exam

## Section A: ABCD

1. In `public List<Pet> listForUser(User user)`, why is `List<Pet>` the right return type?
   - A. The method returns pets in a collection that can contain multiple results.
   - B. The method returns only one pet.
   - C. The method stores key-value pairs.
   - D. The method guarantees unique image MIME types.

2. In `List<LocationDTO>` for pet routes, which collection feature matters most?
   - A. Unique values only.
   - B. Key-value lookup by terminal ID.
   - C. Ordered items.
   - D. Thread-safe writes.

3. In `private final Cache<String, CachedPetLookup> petByTerminalIdCache;`, what is the key?
   - A. `CachedPetLookup`
   - B. `String`
   - C. `Long`
   - D. `LocationDTO`

4. Why is Caffeine better than a raw `ConcurrentHashMap` for `petByTerminalIdCache`?
   - A. It is thread-safe and also supports expiration and maximum size.
   - B. It sorts pets by name automatically.
   - C. It stores duplicate terminal IDs.
   - D. It makes database queries faster.

5. In `private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(...)`, why is `Set` a good choice?
   - A. It allows duplicate MIME types.
   - B. It stores values in timestamp order.
   - C. It stores unique allowed MIME types.
   - D. It maps MIME types to pets.

6. Which literal example creates the allowed image type collection in `PetService`?
   - A. `List.of("image/jpeg", "image/png")`
   - B. `Map.of("image/jpeg", "image/png")`
   - C. `Set.of("image/jpeg", "image/png", "image/webp", "image/gif")`
   - D. `new ArrayList<>("image/jpeg")`

7. In `config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));`, what does `List.of(...)` create?
   - A. A map from HTTP method to URL.
   - B. A list of allowed HTTP methods.
   - C. A set of image MIME types.
   - D. A database table.

8. In `Map.of("error", exception.getMessage())`, why is `Map` the best match?
   - A. It returns one key-value pair for the JSON response.
   - B. It keeps route positions in order.
   - C. It removes duplicate volunteers.
   - D. It runs code asynchronously.

9. In `List<List<Double>> coordinates`, what does the nested `List` represent?
   - A. A key-value cache.
   - B. A list of coordinate pairs, where each pair is also a list.
   - C. A set of unique MIME types.
   - D. A single pet owner.

10. Which collection best matches `Set<User> volunteers = new HashSet<>();` in `SearchGroup`?
    - A. `List`, because volunteers must be stored by index only.
    - B. `Map`, because each volunteer must be stored with a string key.
    - C. `Set`, because the same volunteer should not appear twice.
    - D. `Queue`, because volunteers must be processed first-in first-out.

11. In `if (walkRequests.isEmpty()) { return List.of(); }`, why return `List.of()`?
    - A. It returns an empty list without creating mutable data.
    - B. It returns `null`.
    - C. It creates a map with one empty key.
    - D. It starts a background GPS task.

12. In `List<Long> walkRequestIds = walkRequests.stream().map(WalkRequest::getId).toList();`, what is the final result?
    - A. A `Set<Long>` of unique owners.
    - B. A `Map<Long, WalkRequest>`.
    - C. A `List<Long>` containing walk request IDs.
    - D. A single `Long`.

13. In `Map<Long, Long> activeDogWalkIdsByRequestId`, what does the map connect?
    - A. Walk request id to active dog walk id.
    - B. Pet name to image MIME type.
    - C. HTTP method to controller name.
    - D. Volunteer id to city name.

14. In `.collect(Collectors.toMap(dogWalk -> dogWalk.getWalkRequest().getId(), DogWalk::getId, (first, second) -> first))`, why use `Collectors.toMap(...)`?
    - A. To convert a stream of `DogWalk` objects into a lookup map.
    - B. To convert a map into a stream.
    - C. To sort all dog walks by timestamp.
    - D. To remove all walk requests.

15. In `(first, second) -> first` inside `Collectors.toMap(...)`, what is this function for?
    - A. Handling duplicate keys.
    - B. Creating a `List`.
    - C. Reading an HTTP request body.
    - D. Saving an entity.

16. In `.map(position -> Map.of(dogWalk.getId(), position)).orElseGet(Map::of)`, what does `Map::of` provide when no position exists?
    - A. An empty map.
    - B. A list with one position.
    - C. A set with one dog walk.
    - D. A null pointer.

17. In `Map<Long, DogWalkPosition> latestPositionsByWalkId`, why is `Map` useful?
    - A. It allows direct lookup of latest position by dog walk id.
    - B. It stores positions only in insertion order.
    - C. It guarantees each position has a different timestamp.
    - D. It converts positions to JSON automatically.

18. In `Set<User> volunteers = searchGroup.getVolunteers();`, what should the code do before streaming if the set might be null?
    - A. Check for null and use `List.of()` when missing.
    - B. Convert it to a `String`.
    - C. Save it to the database.
    - D. Change it to `Map.of()`.

19. In `volunteers.stream().map(SearchGroupMapper::toVolunteerMemberDto).toList()`, why use `map`?
    - A. To transform each `User` into a `VolunteerMemberDto`.
    - B. To flatten nested lists.
    - C. To create a thread-safe cache.
    - D. To delete duplicate volunteers from the database.

20. In `private static List<Double> toLatLng(Coordinate coordinate) { return List.of(coordinate.y, coordinate.x); }`, what does the returned `List<Double>` contain?
    - A. Latitude and longitude values.
    - B. A pet id and owner id.
    - C. Two HTTP methods.
    - D. Two image MIME types.

## Section B: True Or False

1. JWT should contain the user's password.
2. `POST /api/pets/{id}/locations` is idempotent by default.
3. `@Transactional(readOnly = true)` should be used for write operations.
4. Caffeine cache helps with thread-safe cache access, expiration, and maximum size.
5. A route endpoint should usually limit the number of returned positions.

## Section C: Fill The Word

1. The Java runtime that executes bytecode is the ______.
2. A database index helps queries run ______.
3. A ______ maps an entity to an API response.
4. ______ means the same request can be repeated with the same final result.
5. A ______ is Kubernetes' smallest deployable unit.

## Section D: Write The Whole Answer

1. Explain the difference between Entity and DTO using `Pet` and `PetDto`.
2. Explain why FiruApp should not reverse-geocode every point in `/route`.
3. Explain why `GpsIngestionService` uses async processing.
4. Explain how JWT makes load balancing easier.
5. Explain why indexes on `location(pet_id, timestamp)` matter.

## Section E: Find The Error

### Error 1

```java
public LocationDTO save(Location location) {
    locationRepository.save(location);
    return locationMapper.toDto(location);
}
```

Question:

What is missing if the API response must include persisted neighborhood fields?

Expected answer:

Call the neighborhood lookup, apply the fields to `Location`, save the enriched entity, then map to DTO.

### Error 2

```java
if (user.getRole() == User.Role.ADMIN || pet.getOwner().getId().equals(user.getId())) {
    return true;
}
```

Question:

What can break?

Expected answer:

`pet.getOwner()` or `pet.getOwner().getId()` can be null. Add null checks.

### Error 3

```java
private HashMap<String, CachedPetLookup> cache = new HashMap<>();
```

Question:

Why is this risky in GPS ingestion?

Expected answer:

GPS ingestion is concurrent, so `HashMap` is unsafe. For this project, use the Caffeine cache bean so the cache is thread-safe and has expiration and maximum-size policy.

### Error 4

```java
@Query("SELECT l FROM Location l")
List<Location> route(Long petId);
```

Question:

What is wrong?

Expected answer:

It returns all rows and ignores `petId`. Add `WHERE l.pet.id = :petId` and pagination.

### Error 5

```java
neighborhood == null ? location.getNeighborhoodResolved() : neighborhood.resolved()
```

Question:

Why can this crash?

Expected answer:

The ternary mixes nullable `Boolean` and primitive `boolean`, causing unboxing of null. Use `Boolean.valueOf(neighborhood.resolved())`.

---

# Practice Projects

## Project 1: Add Custom Exceptions

Goal:

Replace generic `RuntimeException` in one service with custom exceptions.

Steps:

1. Create `NotFoundException`.
2. Create `BadRequestException`.
3. Update `ApiExceptionHandler`.
4. Replace one `RuntimeException("Pet not found")`.
5. Add a test.

## Project 2: Add Idempotency To Location POST

Goal:

Avoid duplicate GPS location saves.

API idea:

```json
{
  "eventId": "tracker-123-seq-999",
  "latitude": 6.2442,
  "longitude": -75.5812,
  "timestamp": "2026-10-06T12:00:00Z"
}
```

Steps:

1. Add `event_id` column to `location`.
2. Add unique index on `(pet_id, event_id)`.
3. Add field to `LocationRequest`.
4. Before save, check if event exists.
5. Return existing row if duplicate.

## Project 3: Add A Batch Backfill

Goal:

Backfill missing `location.neighborhood` fields.

Simple version:

1. Query locations where `neighborhood_resolved IS NULL`.
2. Process 100 at a time.
3. Call `NeighborhoodLookupService`.
4. Save enriched rows.
5. Log progress.

Spring Batch version:

Use reader, processor, writer.

## Project 4: Add API Metrics

Goal:

Track average latency by endpoint.

Steps:

1. Add start time in a filter/interceptor.
2. Record duration after request.
3. Group by method + URI template.
4. Expose in admin metrics.

---

# Final Review Checklist

Before an interview or exam, be able to explain:

- What a Spring controller does.
- Why DTOs exist.
- How JPA maps entities.
- How repositories create SQL.
- What transactions do.
- Why indexes matter.
- How JWT auth works.
- How authorization differs from authentication.
- How async GPS ingestion works.
- Why thread-safe collections matter.
- What idempotency means.
- What stateless means.
- How Docker builds the app.
- What Kubernetes adds.
- Why logs and metrics matter.
- How to debug a production error.
- How to design a clean REST endpoint.
- How to calculate daily distance from GPS points.
