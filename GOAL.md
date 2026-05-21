# Goal

## Idea

I want to write a clojure (start with babashka) that allows to fuse llm and classical programming.
For this you write normal clojure code and can use functions that will be filled in with provided content or systems.
The code we write with this can then be translated into mulitple different languages depending on the defined behavior.

For example:

```clojure
(->> [{:lang :clojure
       :input-api input-api
       :output-api output-api
       :desc "desc"}]
     (map drill/feature)
     (dosomething)
```

## List of functions

- feature
  - smaller feature is implemented by the llm.
  - A new 'namespace' will be used as a function
  - Runs eventually inside of the main process
- system
  - a whole system implementation
  - this can be a larger thingy that runs outside of the main process
- produce
  - The llm runs an analyses on the input and produces the specified output
- api
  - llm implements an api implementation.

## Result of the repo

should be the necessary clojure / babashka code and the necessary skills
e.g. to work with claude code.
