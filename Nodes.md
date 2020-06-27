

Covariance Index: given modules m1 and m2 in a graph of data/field dependencies weighted by the count of the data elements*
used in the interface, the covariance index is the 'cross section' of the interface (sum of weights of dependencies between the
tow) divided by the sum of the volumes (sum of dependency weights) of the subgraphs given when each module is cut from the rest of the graph.

Normally, this should be << 1.0, but other forces may lead to modules being more tightly coupled than that:
*. Conjecture 1.1 Layered architectures in a data-heavy, behavior light domain will tend towards a higher covariance between the
layer modules for that domain.

Covariance index gives a notion of how 'orthogonal' the two modules are - how much can one change without the other being changed.
Note that this is _literally_ orthogonal - it is analogous to cosign similarity. In fact:
Given a weighted Laplacian L of a reference graph G and two functions f() and g() generating a selection vector for each
module, <Lf(), Lg()> should actually be a very good measure of the covariance (aka coupling).
Note that G can be contracted at any level of granularity and the result will hold. This isn't the same as the 'cross section'
above - this is a measure of how many nodes each is related to within a radius of 1 edge. This implies that the general
case is <L^nf(), L^Ng()>, and the progression of this value as n increased will be interesting (note that the sum of that
series is total common elements>



Another interesting number might be to calculate a modules overall surface area vs. its volume - a high ratio indicates
leaky abstractions. Which _might_ be bad, or might be the point.