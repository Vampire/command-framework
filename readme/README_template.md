Command Framework
=================

[![Version][Version Badge]][Latest Release]
[![JavaDoc][JavaDoc Badge]][Latest JavaDoc]
[![License][License Badge]][License File]
[![Discord][Discord Badge]][Discord Invite]

![Unit Test Coverage Badge]
![Mutant Coverage Badge]
![Integration Test Coverage Badge]

[![Supported Java Versions][Supported Java Versions Badge]](#)

[![Supported Message Frameworks][Supported Message Frameworks Badge]](#supported-message-frameworks)

A generic CDI-based command framework. This library requires Java 8 or newer but is fully Java 9+ compatible and can run
as a proper Java module on the module path. Any arbitrary underlying message framework like a Discord library, an IRC
library, or a Skype library can be used by providing an according [`CommandHandler`][CommandHandler JavaDoc]
implementation. You are also welcome to contribute such implementations back to the main project for all users benefit.



Table of Contents
-----------------
* [Prerequisites](#prerequisites)
* [Supported Message Frameworks](#supported-message-frameworks)
* [Setup](#setup)
  * [Gradle](#gradle)
  * [Maven](#maven)
  * [Manually](#manually)
* [Usage](#usage)
  * [Message Framework](#message-framework)
    * [Javacord](#javacord)
    * [JDA](#jda)
  * [Creating Commands](#creating-commands)
    * [Command Aliases](#command-aliases)
    * [Asynchronous Command Execution](#asynchronous-command-execution)
    * [Command Restrictions](#command-restrictions)
    * [Command Usage](#command-usage)
    * [Parsing Parameters](#parsing-parameters)
      * [Simple Splitting of Parameters](#simple-splitting-of-parameters)
      * [Semantic Parsing and Validation](#semantic-parsing-and-validation)
      * [Semantic Parsing and Validation with Type Conversions](#semantic-parsing-and-validation-with-type-conversions)
    * [Customizing Parameter Converters](#customizing-parameter-converters)
    * [Customizing Command Prefix](#customizing-command-prefix)
    * [Customizing Alias Calculation](#customizing-alias-calculation)
  * [CDI Events](#cdi-events)
    * [Handling Missing Commands](#handling-missing-commands)
    * [Handling Disallowed Commands](#handling-disallowed-commands)
  * [Getting the Library Version Programmatically](#getting-the-library-version-programmatically)
  * [Supporting other Message Frameworks](#supporting-other-message-frameworks)
* [Version Numbers](#version-numbers)
* [License](#license)



Prerequisites
-------------
* Java 8+
* One of the supported [message frameworks](#supported-message-frameworks)
* An implementation of CDI that implements CDI $cdiVersion like [Weld SE][Weld SE Website]
* [Optional] ANTLR runtime $antlrVersion if the [`ParameterParser`][ParameterParser JavaDoc] is used



Supported Message Frameworks
----------------------------

The following message frameworks are currently supported:

* [Javacord](#javacord)
* [JDA](#jda)

If you want to have support for an additional, do not hesitate to open a PR or feature request issue.



Setup
-----

### Gradle

```gradle
repositories { mavenCentral() }
dependencies { implementation 'net.kautler:command-framework:$version' }
```

### Maven

```xml
<dependency>
  <groupId>net.kautler</groupId>
  <artifactId>command-framework</artifactId>
  <version>$version</version>
</dependency>
```

### Manually

Download the JAR for the latest release from the [Latest Release Page][Latest Release] and include it in your project.



Usage
-----

### Message Framework

#### Javacord

For the [Javacord][Javacord Website] support, include Javacord as implementation dependency and create a CDI producer
that produces either one `DiscordApi`, or if you use sharding a `Collection<DiscordApi>` with all shards where you want
commands to be handled. You should also have a disposer method that properly disconnects the produced `DiscordApi`
instances.

_**Example:**_
```java
@ApplicationScoped
public class JavacordProducer {
    @Inject
    private Logger logger;

    @Inject
    @Named
    private String discordToken;

    @Produces
    @ApplicationScoped
    private DiscordApi produceDiscordApi() {
        return new DiscordApiBuilder()
                .setToken(discordToken)
                .login()
                .whenComplete((discordApi, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while logging in to Discord", throwable);
                    }
                })
                .join();
    }

    private void disposeDiscordApi(@Disposes DiscordApi discordApi) {
        discordApi.disconnect();
    }
}
```

_**Tested versions:**_
$testedJavacordVersions

#### JDA

For the [JDA][JDA Website] support, include JDA as implementation dependency and create a CDI producer that produces
either one `JDA`, of if you use sharding a `Collection<JDA>`, a `ShardManager` or a `Collection<ShardManager>` with all
shards where you want commands to be handled. You should also have a disposer method that properly shuts down the
produced `JDA` and / or `ShardManager` instances.

_**Example:**_
```java
@ApplicationScoped
public class JdaProducer {
    @Inject
    private Logger logger;

    @Inject
    @Named
    private String discordToken;

    @Produces
    @ApplicationScoped
    private JDA produceJda() {
        try {
            return new JDABuilder(discordToken)
                    .build()
                    .awaitReady();
        } catch (InterruptedException | LoginException e) {
            logger.error("Exception while logging in to Discord", e);
            return null;
        }
    }

    private void disposeJda(@Disposes JDA jda) {
        jda.shutdown();
    }
}
```

_**Tested versions:**_
$testedJdaVersions

### Creating Commands

Create a CDI bean that implements the [`Command`][Command JavaDoc] interface.

_**Example:**_
```java
@ApplicationScoped
public class PingCommand implements Command<Message> {
    @Override
    public void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
        incomingMessage.getChannel()
                .sendMessage("pong: " + parameterString)
                .exceptionally(ExceptionLogger.get());
    }
}
```

With everything else using the default, this is already enough to have a working ping bot.
A fully self-contained example can be found at `examples/simplePingBotJavacord`.

To further customize the behavior of a command you can either annotate the command class or overwrite the according
methods in your command implementation to replace the default implementation which evaluates the annotations.
Having annotations applied and at the same time overwriting the according methods makes only sense if you want the
annotations only for documentary purpose or evaluate them yourself, as the default implementations of those methods
are the only places where the annotations are evaluated by default. Every other place like for example command handler
call the methods on the command implementation.

#### Command Aliases

By overwriting the `Command#getAliases()` method or applying one or multiple [`@Alias`][@Alias JavaDoc] annotations the
aliases to which the command reacts can be configured. If at least one alias is configured, only the explicitly
configured ones are available. If no alias is configured, the class name, stripped by `Command` or `Cmd`
suffix and / or prefix and the first letter lowercased is used as default.

#### Asynchronous Command Execution

By overwriting the `Command#isAsynchronous()` method or applying the [`@Asynchronous`][@Asynchronous JavaDoc] annotation
the command handler can be told to execute the command asynchronously.

How exactly this is implemented is up to the command handler that evaluates this command. Usually the command will be
execute in some thread pool. But it would also be valid for a command handler to execute each asynchronous command
execution in a new thread, so using this can add significant overhead if overused. As long as a command is not doing
long-running or blocking operations it might be a good idea to not execute the command asynchronously. But if
long-running or blocking operations are done in the command code directly, depending on the underlying message framework
it might be a good idea to execute the command asynchronously to not block message dispatching which could introduce
serious lag to the command execution.

As the command executions are potentially done on different threads, special care must be taken if the command holds
state, to make sure this state is accessed in a thread-safe manner. This can of course also happen without the command
being configured asynchronously if the underlying message framework dispatches message events on different threads.

#### Command Description

By overwriting the `Command#getDescription()` method or applying the [`@Description`][@Description JavaDoc] annotation
the description of the command can be configured. Currently this description is used nowhere, but can for example be
displayed in an own help command.

#### Command Restrictions

By overwriting the `Command#getRestrictionChain()` method or applying one or multiple
[`@RestrictedTo`][@RestrictedTo JavaDoc] annotations and optionally the
[`@RestrictionPolicy`][@RestrictionPolicy JavaDoc] annotation the restriction rules for a command can be configured. If
multiple `@RestrictedTo` annotations are present and the default implementation of the method is used, a
`@RestrictionPolicy` annotation that defines how the single restrictions are to be combined is mandatory. With this
annotation the single restrictions can be combined in an all-of, any-of, or none-of logic.

For more complex boolean logic either overwrite the `getRestrictionChain` method or provide own CDI beans that
implement the [`Restriction`][Restriction JavaDoc] interface and contain the intended logic. For the latter also helpers
like [`ChannelJavacord`][ChannelJavacord JavaDoc], [`RoleJavacord`][RoleJavacord JavaDoc],
[`ServerJavacord`][ServerJavacord JavaDoc], [`UserJavacord`][UserJavacord JavaDoc], [`AllOf`][AllOf JavaDoc],
[`AnyOf`][AnyOf JavaDoc], or [`NoneOf`][NoneOf JavaDoc] can be used as super classes.

_**Examples:**_
```java
@ApplicationScoped
public class Vampire extends UserJavacord {
    private Vampire() {
        super(341505207341023233L);
    }
}
```

```java
@ApplicationScoped
public class MyFancyServer extends ServerJavacord {
    @Inject
    private MyFancyServer(@Named("myFancyServerId") long myFancyServerId) {
        super(myFancyServerId);
    }
}
```

#### Command Usage

By overwriting the `Command#getUsage()` method or applying the [`@Usage`][@Usage JavaDoc] annotation the usage of the
command can be configured. This usage can for example be displayed in an own help command.

When using the [`ParameterParser`][ParameterParser JavaDoc], the usage string has to follow a pre-defined format that
is described at [Parsing Parameters](#parsing-parameters).

#### Parsing Parameters

There are three helpers to split the `parameterString` that is provided to `Command#execute(...)` into multiple
parameters that can then be handled separately.

##### Simple Splitting of Parameters

The first is the method `Command.getParameters(...)` which you give the parameter string and the maximum amount of
parameters to split into. The provided string will then be split at any arbitrary amount of consecutive whitespace
characters. The last element of the returned array will have all remaining text in the parameter string. If you expect
exactly three parameters without whitespaces, you should set the max parameters to four, so you can easily test the
length of the returned array whether too many parameters were given to the command.

##### Semantic Parsing and Validation

The second is the [`ParameterParser`][ParameterParser JavaDoc] that you can get injected into your command. For the
`ParameterParser` to work, the [usage](#command-usage) of the command has to follow a defined syntax language. This
usage syntax is then parsed and the given parameter string analysed according to the defined syntax. If the given
parameter string does not adhere to the defined syntax, a `ParameterParseException` is thrown that can be caught and
reported to the user giving wrong arguments. The exception message is suitable to be directly forwarded to users.

The usage string has to follow this pre-defined format:
* Placeholders for free text without whitespaces (in the value) look like `<my placeholder>`
* One placeholder for free text with whitespaces (in the value) is allowed as effectively last parameter and looks like
  `<my placeholder...>`
* Literal parameters look like `'literal'`
* Optional parts are enclosed in square brackets like `[<optional placeholder>]`
* Alternatives are enclosed in parentheses and are separated by pipe characters like `('all'  | 'some'  | 'none')`
* Whitespace characters between the defined tokens are optional and ignored

_**Examples:**_
* `@Usage("<coin type> <amount>")`
* `@Usage("['all'] ['exact']")`
* `@Usage("[<text...>]")`
* `@Usage("(<targetLanguage> '|' | <sourceLanguage> <targetLanguage>) <text...>")`

The values for these non-typed parameters are always `String`s unless multiple parameters with the same name have a
value given by the user like with the pattern `<foo> <foo>`, in which case the value will be a `List<String>`.

_**Warning:**_ If you for example have
* an optional placeholder followed by an optional literal like in `[<placeholder>] ['literal']` or
* alternatively a placeholder or literal like in `(<placeholder> | 'literal')`

and a user invokes the command with only the parameter `literal`, it could fit in both parameter slots.
You have to decide yourself in which slot it belongs. For cases where the literal parameter can never
be meant for the placeholder, you can use `Parameters#fixup(...)` to correct the parameters instance
for the two given parameters.

##### Semantic Parsing and Validation with Type Conversions

The third is an addendum to the second method described above. The syntax is basically the same. The only difference is,
that a colon (':') followed by a parameter type can optionally be added after a parameter name like for example
`<amount:integer>`. Parameters that do not have a type specified, are implicitly of type `string`. If a colon is needed
within the actual parameter name, a type has to be specified explicitly, as invalid parameter types are not allowed and
will trigger an error at runtime.

The parameter types can be freely defined by supplying [parameter converters](#customizing-parameter-converters) to
define new types or overwrite built-in ones to have a different behavior.

The built-in types currently available are:
* for all message frameworks
  * `decimal` for a floating point number converted to `BigDecimal`
  * `number` or `integer` for a natural number converted to `BigInteger`
  * `string` or `text` for a no-op conversion which can be used if a colon is needed in the parameter name
    or if simply all parameters should have a type specified for consistency
* for Javacord
  * `user_mention` or `userMention` for a mentioned user converted to `User`
  * `role_mention` or `roleMention` for a mentioned role converted to `Role`
  * `channel_mention` or `channelMention` for a mentioned channel converted to `Channel`
* for JDA
  * `user_mention` or `userMention` for a mentioned user converted to `User`
  * `role_mention` or `roleMention` for a mentioned role converted to `Role`
  * `channel_mention` or `channelMention` for a mentioned channel converted to `TextChannel`

To select the typed parameter parser, add the qualifier `@ParameterParser.Typed` to the injected `ParameterParser`.

_**Examples:**_
```java
@ApplicationScoped
@Usage("<text...>")
public class PingCommand implements Command<Message> {
    @Inject
    private ParameterParser parameterParser;

    @Override
    public void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
        try {
            parameterParser.parse(this, incomingMessage, prefix, usedAlias, parameterString);
        } catch (ParameterParseException ppe) {
            incomingMessage.getChannel()
                    .sendMessage(format("%s: %s", incomingMessage.getAuthor().getDisplayName(), ppe.getMessage()))
                    .exceptionally(ExceptionLogger.get());
            return;
        }

        incomingMessage.getChannel()
                .sendMessage("pong: " + parameterString)
                .exceptionally(ExceptionLogger.get());
    }
}
```

```java
@ApplicationScoped
@Usage("[<user:userMention>] ['exact']")
public class DoCommand implements Command<Message> {
    @Inject
    @Typed
    private ParameterParser parameterParser;

    @Override
    public void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
        Parameters<String> parameters;
        try {
            parameters = parameterParser.parse(this, incomingMessage, prefix, usedAlias, parameterString);
        } catch (ParameterParseException ppe) {
            incomingMessage.getChannel()
                    .sendMessage(format("%s: %s", incomingMessage.getAuthor().getDisplayName(), ppe.getMessage()))
                    .exceptionally(ExceptionLogger.get());
            return;
        }
        parameters.fixup("user mention", "exact");
        boolean exact = parameters.containsParameter("exact");
        Optional<String> otherUser = parameters.get("user mention");
        // ...
    }
}
```

#### Customizing Parameter Converters

A custom parameter converter can be configured by providing a CDI bean that implements the
[`ParameterConverter`][ParameterConverter JavaDoc] interface. In the implementation of the `convert` method the string
parameter and various context information is given and from this the converted parameter value can be calculated.
The class also needs to be annotated with one or multiple [`ParameterType`][ParameterType JavaDoc] qualifiers
that define the parameter type aliases for which the annotated parameter converter works. Without such
qualifier the converter will simply never be used. It is an error to have multiple parameter converters with the
same parameter type that can be applied to the same framework message type and this will produce an error latest
when a parameter with that type is being converted. The only exception are the built-in parameter types.
A user-supplied converter with the same parameter type as a built-in converter will be preferred,
but it would still be an error to have multiple such overrides for the same type.

_**Examples:**_
```java
@ApplicationScoped
@ParameterType("strings")
public class StringsConverter implements ParameterConverter<Object, List<String>> {
    @Override
    public List<String> convert(String parameter, String type, Command<?> command, Object message,
                                String prefix, String usedAlias, String parameterString) {
        return asList(parameter.split(","));
    }
}
```

#### Customizing Command Prefix

A custom command prefix can be configured by providing a CDI bean that implements the
[`PrefixProvider`][PrefixProvider JavaDoc] interface. In the implementation of the `getCommandPrefix` method you can
determine from the message that caused the command what the command prefix should be. This way you can for example have
different command prefixes for different servers your bot is serving. If no custom prefix provider is found, a default
one is used, that always returns `"!"` as the prefix.

There are also helper classes that can be used as super classes for own prefix providers like for example a provider
that returns the mention string for the bot as command prefix if Javacord is used as the underlying message framework.

_**Warning:**_ The command prefix can technically be configured to be empty, but this means that every message will be
checked against a regular expression and that for every non-matching message a CDI event will be sent. It is better for
the performance if a command prefix is set instead of including it in the aliases directly. Due to this potential
performance issue, a warning is logged each time a message is handled with an empty command prefix. If you do not care
and want the warning to vanish, you have to configure your logging framework to ignore this warning, as it also costs
additional performance and might hide other important log messages. ;-)

_**Examples:**_
```java
@ApplicationScoped
public class MyPrefixProvider implements PrefixProvider<Message> {
    @Override
    public String getCommandPrefix(Message message) {
        Optional<Server> server = message.getServer();
        if (!server.isPresent()) {
            return "!";
        } else if (server.get().getId() == 12345L) {
            return "bot ";
        } else {
            return ":";
        }
    }
}
```

```java
@ApplicationScoped
public class MentionPrefixProvider extends MentionPrefixProviderJavacord {
}
```

#### Customizing Alias Calculation

The alias calculation can be customized by providing a CDI bean that implements the
[`AliasAndParameterStringTransformer`][AliasAndParameterStringTransformer JavaDoc] interface. In the implementation of
the `transformAliasAndParameterString` method you can determine from the message that caused the processing what the
alias and parameter string should be. The transformer is called after the alias and parameter string are determined from
the message using all registered aliases and before the command is resolved from the alias. If an alias was found from
the registered aliases, the `aliasAndParameterString` parameter contains the found information. If no alias was found,
the parameter will be `null`. The fields in the `AliasAndParameterString` object are always non-`null`.

The transformer can then either accept the found alias and parameter string by returning the argument directly, or it
can determine a new alias and parameter string and return these. The return value of the transformer will be used for
further processing. If the alias in the returned object is not one of the registered aliases or the transformer returns
`null`, there will not be any command found and the respective CDI event will be fired.

Example use-cases for this are:

- fuzzy-searching for mistyped aliases and their automatic correction (this could also be used for just a
  "did you mean X" response, but for that the command not found events are probably better suited)

- having a command that forwards to one command in one channel but to another command in another channel,
  like `!player` that forwards to `!mc:player` in an MC channel but to `!s4:player` in an S4 channel

- supporting something like `!runas @other-user foo bar baz`, where the transformer will transform that to alias
  `foo` and parameter string `bar baz` and then a custom `Restriction` can check whether the message author has
  the permissions to use `!runas` and then for example whether the `other-user` would have permissions for the
  `foo` command and only then allow it to proceed

- forwarding to a `!help` command if an unknown command was issued

_**Example:**_
```java
@ApplicationScoped
public class MyAliasAndParameterStringTransformer implements AliasAndParameterStringTransformer<Message> {
    @Override
    public AliasAndParameterString transformAliasAndParameterString(
            Message message, AliasAndParameterString aliasAndParameterString) {
        return (aliasAndParameterString == null)
                ? new AliasAndParameterString("help", "")
                : aliasAndParameterString;
    }
}
```

### CDI Events

#### Handling Missing Commands

If a message starts with the configured command prefix, but does not map to an available command, the command handlers
send an async CDI event that you can observe and handle to react accordingly like sending a message that a command was
not found.

_**Example:**_
```java
@ApplicationScoped
public class EventObserver {
    private void commandNotFound(@ObservesAsync CommandNotFoundEventJavacord commandNotFoundEvent) {
        commandNotFoundEvent.getMessage()
                .getChannel()
                .sendMessage(format(
                        "Command %s%s was not found!",
                        commandNotFoundEvent.getPrefix(),
                        commandNotFoundEvent.getUsedAlias()))
                .exceptionally(ExceptionLogger.get());
    }
}
```

#### Handling Disallowed Commands

If a command was found but not allowed by some [restriction rules](#command-restrictions), the command handlers send an
async CDI event that you can observe and handle to react accordingly like sending a message that a command was not
allowed.

_**Example:**_
```java
@ApplicationScoped
public class EventObserver {
    private void commandNotAllowed(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
        commandNotAllowedEvent.getMessage()
                .getChannel()
                .sendMessage(format(
                        "Command %s%s was not allowed!",
                        commandNotAllowedEvent.getPrefix(),
                        commandNotAllowedEvent.getUsedAlias()))
                .exceptionally(ExceptionLogger.get());
    }
}
```

### Getting the Library Version Programmatically

You are welcome to mention in some "about" command or similar that you use this library to attract other people to use
it too. If you want to also mention which version you are using, you can use the `getDisplayVersion` method of the
[`Version`][Version JavaDoc] CDI bean by injecting it into your code.

### Supporting other Message Frameworks

If you want to support a message framework that is not natively supported already, you need to provide a CDI bean that
extends the [`CommandHandler`][CommandHandler JavaDoc] class. You are also welcome to contribute back any such
implementation to the library for all users benefit. You should read the JavaDoc of the `CommandHandler` class and have
a look at any of the existing implementations to get started with writing your own implementation. Most of the common
logic should be done in the `CommandHandler` class already and just some framework-dependent things like attaching
message listeners to the underlying framework need to be done in the sub-class.



Version Numbers
---------------

Versioning of this library follows the [Semantic Versioning][Semantic Versioning Website] specification.
But only classes in packages starting with `net.kautler.command.api` are bound to the backwards compatibility
constraints of semantic versioning. All other classes are considered internal and can have breaking changes anytime.



License
-------

```
Copyright 2020 Björn Kautler

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```



[Version Badge]:
    https://shields.javacord.org/maven-central/v/net.kautler/command-framework.svg?label=Version
[JavaDoc Badge]:
    https://shields.javacord.org/badge/JavaDoc-Latest-yellow.svg?style=flat
[License Badge]:
    https://shields.javacord.org/github/license/Vampire/command-framework.svg?label=License
[License File]:
    https://github.com/Vampire/command-framework/blob/master/LICENSE
[Discord Badge]:
    https://shields.javacord.org/discord/534420861294346255.svg?label=Discord
[Unit Test Coverage Badge]:
    https://shields.javacord.org/badge/Unit%20Test%20Coverage-100%25-brightgreen.svg?style=flat
[Mutant Coverage Badge]:
    https://shields.javacord.org/badge/PIT%20Mutant%20Coverage-100%25-brightgreen.svg?style=flat
[Integration Test Coverage Badge]:
    https://shields.javacord.org/badge/Integration%20Test%20Coverage-~70%25-brightgreen.svg?style=flat
[Supported Java Versions Badge]:
    https://shields.javacord.org/badge/Supported%20Java%20Versions-Java8+-lightgrey.svg
[Supported Message Frameworks Badge]:
    https://shields.javacord.org/badge/Supported%20Message%20Frameworks-Javacord%20%7C%20JDA-lightgrey.svg
[Weld SE Website]:
    https://docs.jboss.org/weld/reference/latest/en-US/html/environments.html#weld-se
[Semantic Versioning Website]:
    https://semver.org

[Latest Release]:
    https://github.com/Vampire/command-framework/releases/latest
[Latest JavaDoc]:
    https://www.javadoc.io/doc/net.kautler/command-framework
[Discord Invite]:
    https://discord.gg/p2BuzQh

[Javacord Website]:
    https://javacord.org
[JDA Website]:
    https://github.com/DV8FromTheWorld/JDA

[@Alias JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/Alias.html
[AllOf JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/AllOf.html
[AnyOf JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/AnyOf.html
[@Asynchronous JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/Asynchronous.html
[ChannelJavacord JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/javacord/ChannelJavacord.html
[Command JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/Command.html
[CommandHandler JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/CommandHandler.html
[@Description JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/Description.html
[NoneOf JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/NoneOf.html
[ParameterConverter JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/parameter/ParameterConverter.html
[ParameterParser JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/parameter/ParameterParser.html
[ParameterType JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/parameter/ParameterType.html
[PrefixProvider JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/prefix/PrefixProvider.html
[AliasAndParameterStringTransformer JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/AliasAndParameterStringTransformer.html
[@RestrictedTo JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/RestrictedTo.html
[Restriction JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/Restriction.html
[@RestrictionPolicy JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/RestrictionPolicy.html
[RoleJavacord JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/javacord/RoleJavacord.html
[ServerJavacord JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/javacord/ServerJavacord.html
[@Usage JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/annotation/Usage.html
[UserJavacord JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/restriction/javacord/UserJavacord.html
[Version JavaDoc]:
    https://www.javadoc.io/page/net.kautler/command-framework/latest/net/kautler/command/api/Version.html
