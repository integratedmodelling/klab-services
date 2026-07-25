1. Agent#ask pattern still unimplemented; must review API then use messaging system to implement.
2. Provide a `switch` statement with the same syntax as a match expression, to use in a blocking statement on a variable
   and give it value semantics if it's on the right side of an assignment or used in other value contexts (if, for,
   while, do - need to see what is compatible). It will also need a `yield` verb to implement functional behavior within
   group actions without violating the `return` contract.
3. Provide a match syntax for a behavior using its full URN path that can match an agent's or an agent handle's
   behavior, considering also any inherited traits or behaviors.
4. A behavior's inheritance list is now made of Import objects that can carry a local name to enable using a `super`
   like syntax on overridden methods. This should be supported by the compiler.
5. Provide Java @Actor definitions that apply to a known Java type (e.g. Number or String) so that a literal can receive
   messages and gets compiled into a call to the (static) verb. So `"string".substring(x)` becomes possible as long as
   the string is in a variable.
6. Values with casts would be good to have, as well as functional message calls and other functional expressions in parameter lists.