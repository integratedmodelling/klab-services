# Observables, observations, models

This chapter is about _observable queries_, or in short _observables_: the logical queries that k.LAB deals with, introduced [previously](index.adoc) with some examples. We discuss how they are defined and what observations can be expected to come from them. This chapter is written and should be read from the perspective of a user or modeler: this means that we assume that a _worldview_ has already been defined and chosen, so there is a pre-defined, searchable repository of logically consistent concepts that we can use and combine with operators to produce observables.

**Concepts** are the basic building blocks of any observable, and in fact many concepts can be used directly as queries. In written expressions, they take the form `namespace:ConceptName`: the element before the colon is the _namespace_, i.e. the knowledge space to which the concept belongs, and may be a single lowercase name or a  _path_ composed of dot-separated names, such as `hydrology.physical`, where each names that comes after the dot implies a more specific subspace of the previous one. The part after the dot, the _concept name_, is written using "camel case" conventions (the first word starting with either case, then the following words having an initial uppercase letter): e.g. `hydrology.physical:WaterFlow`. We always refer to those two elements as a unit, so concepts in different namespaces may have the same name without conflict.

Concepts can belong to a few different basic types. The first distinction is between _observable_ concepts, of which observations can be made, and _predicates_, that cannot exist alone but serve to further specify observables. In linguistic terms, these are broadly equivalent to _nouns_ and _adjectives_, respectively. These may be combined by simply mentioning them together, as in everyday English, or if needed connecting them through the use of _semantic operators_. In general, though, all observable queries must contain _one_ main observable and can contain as many predicates and operators as needed.

## Observable concepts

Observable concepts belong to one of six possible categories, which are fairly easy to understand, although the thinking process behind them is quite involved. These categories are so important that we color-code them for easy reference; even the k.LAB modeler editor uses the same colors to ease understanding. We give first a narrative description of the categories and then try to fit them into a unifying explanation. The philosophical background on which all these notions are built is the [ODO-IM ontology](ODO_IM.md), to which interested readers can refer.

To structure these classes of observables and clearly define how to attribute them to our everyday objects of study, we use two conceptual dimensions, well known to philosophers and relatively straightforward to understand.

1. Observations can be made from two main perspectives, distinguished by their treatment of persistence, or more simply, by the underlying view of time. Foundational scientific ontologies commonly distinguish between entities that are (such as objects like a city or a car) and entities that happen (such as events or processes), using terms like continuant versus occurrent (BFO) or endurant versus perdurant (DOLCE). Rather than making philosophical claims about the nature of entities, we focus on how observations are described and adopt terminology familiar to scientists. Accordingly, we speak of perspectives of description rather than ontological categories. In this framework, persistence is addressed by distinguishing a structural perspective, in which an observable (e.g., a physical object) is described without reference to temporal change, from a functional perspective, in which descriptions are inherently tied to time and change.
2. In addition, we distinguish three fundamental classes of observables based on the number of other entities required to make a meaningful observationâ€”that is, the _arity_ of their description: _independent_, _dependent_, and _relational_. Independent observables can be described on their own, whereas dependent and relational observables must inhere in or relate to other entities to be fully specified. Although these distinctions are well known in philosophyâ€”appearing, for example, in discussions of substantial and dependent entitiesâ€”we adopt them here strictly as descriptive tools rather than as philosophical commitments.

All observable concepts pertain to one of the six classes resulting from intersecting the three dependence categories with the two persistence perspectives, as summarized in Table 1.

| | 2+^|*Observation perspective*|
|---|---|---|---|
|*Arity* |*Dependence* |*Structural* |*Functional*|
|`0` |Independent|Subject|Event|
|`1` |Dependent|Quality|Process|
|`2` |Relational|Structural relationship|Functional relationship|

EXAMPLES HERE

## Configurations

Before we move on, we also need to mention a further category of observables that stands on its own: the _configuration_. Configurations can be described as _patterns_ that form in the human mind when faced with certain observables: a good example is a network, like a river network or even something less material like a family tree. These stand alone because they are not part of the physical world, but they _emerge_ in the mind after certain observations are made. Configurations can be extremely important in science as very often they are the context for mathematical or conceptual descriptions that are crucial to our understanding of the world. But as they are only mental constructs, they cannot be the object of a query, or be explicitly put into a context: rather, they are _detected_ based on what observations are made, driven by the semantics of the observations in a context, and models can be made of them to _explain_ them after they are detected. So for example....

In the case of configurations, the _observer_ is crucial: different observers may build different configurations when faced with the same observables. Formally, they are treated as dependents of arity 1, and their perspective may be structural or functional, depending on the observations they emerge from. Configurations and observers are relatively advanced topics, so we will leave these alone for the time being, and come back to them in the dedicated chapters.


## Predicates

Before getting to descriptions, we must mention another class of concepts, _predicates_, which can be linked to observabels to further describe them when their full description through observations is not possible or when the description is linked to the observation context/process/agent rather than directly to the observed entity. Predicates cannot be used by themselves but always refer, implicitly or explicitly, to some observable concept. Specific classes of description attribute predicates to observations. The observations must have been made already, therefore such descriptions are to be considered dependent.

### Essential predicates vs. attributes, and roles

### Essential predicate redistribution in generic concepts

...

Concepts with arity 1 or higher are called _countables_ and we will use this term to refer to both. In both perspectives, independent and relational observables are _countable_ and can be both _instantiated_ and _resolved_; for dependents, instantiation is implicit and only _resolution_ descriptions can be given.

Again, it is important to remember that the dependence or temporal perspectives are  In k.LAB all ontological statements are interpreted this way; the described entities may be seen as the worldview pleases, and as long as the terminology is consistent there is no other assumption of concern for the implementation. Also, the choice between representing the targets with structural or functional perspectives depends on the choice of description given of time - as a unchanging delimited duration or as a dynamic extent onto which notions of "change" and "currency" can be mapped.

## Modeling in a semantic world

In scientific work, a _model_ is a simplified representation of a real system or phenomenon (conceptual, mathematical, physical, or computational) that captures key features so researchers can explain observations, test hypotheses, and make predictions under stated assumptions. Commonly, scientific models take the form of computations (e.g., software programs or equations) that once computed will produce a "result" representing the desired phenomenon, given a configuration set as input (for example, a set of initial conditions including the spatial and temporal extents of calculation).

In k.LAB, a model is a generalized, formal recipe that links a concept (the observable) to the method that produces a scientific artifact (an observation) describing that concept within a specified context - the _where_ and _when_ that localize the computed results. Running the model is therefore best understood as an observation process: it generates the observation of that concept for the chosen context.

Models will perform different actions according to the semantics of the observable. The main categories of models are only few and clearly separated; understanding how observable semantics defines the type of observation made and the category of the model is key to fully utilizing k.LAB's potential. 

### Types of models vs. semantics

We show examples of each main model type and the semantics that trigger it. Although the examples use observables and operators that are not yet fully defined, they should be understandable; complete documentation of the observable syntax follows later.

The first five model types apply to _qualities_

#### Measurement

model geography:Elevation in m

#### Categorization

model type of Vegetation within Region // within specializes `applies to` !

#### Verification

model presence of Tree within Region

#### Quantification

ranking (priority), percentage/proportion, ratio. No unit, may have a range (implicit in perc/prop, optional in ranking and inferrable in ratio if the operands have a range)

#### Valuation

model value of X
model Income

#### Simulation

model Process

#### Instantiation

model each Substantial

#### Explanation

model Substantial

#### Connection

model each Relationship

#### Classification

model AbstractAttribute of each Substantial

#### Characterization

model ConcreteAttribute of Substantial

models the consequence of having an attribute assigned to a substantial

#### Transformation

model ConcreteAttribute of Quality




