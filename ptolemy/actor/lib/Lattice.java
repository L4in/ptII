/* An FIR lattice filter.

 Copyright (c) 1998-2002 The Regents of the University of California.
 All rights reserved.
 Permission is hereby granted, without written agreement and without
 license or royalty fees, to use, copy, modify, and distribute this
 software and its documentation for any purpose, provided that the above
 copyright notice and the following two paragraphs appear in all copies
 of this software.

 IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 ENHANCEMENTS, OR MODIFICATIONS.

                                        PT_COPYRIGHT_VERSION_2
                                        COPYRIGHTENDKEY

@ProposedRating Yellow (eal@eecs.berkeley.edu)
@AcceptedRating Yellow (cxh@eecs.berkeley.edu)
*/

package ptolemy.actor.lib;

import ptolemy.data.ArrayToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.Variable;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.*;

//////////////////////////////////////////////////////////////////////////
//// Lattice
/**
An FIR filter with a lattice structure.  The coefficients of such a
filter are called "reflection coefficients."  Lattice filters are
typically used as linear predictors because it is easy to ensure that
they are minimum phase, and hence that their inverse is stable.
A lattice filter is (strictly) minimum phase if its reflection
coefficients are all less than unity in magnitude.  To get the
reflection coefficients for a linear predictor for a particular
random process, you can use the LevinsonDurbin actor.
The inputs and outputs are of type double.
<p>
The default reflection coefficients correspond to the optimal linear
predictor for an AR process generated by filtering white noise with
the following filter:
<pre>
                           1
H(z) =  --------------------------------------
        1 - 2z<sup>-1</sup> + 1.91z<sup>-2</sup> - 0.91z<sup>-3</sup> + 0.205z<sup>-4</sup>
</pre>
<p>
Since this filter is minimum phase, the transfer function of the lattice
filter is <i>H <sup>-</i>1<i></sup> </i>(<i>z</i>).
<p>
Note that the definition of reflection coefficients is not quite universal
in the literature. The reflection coefficients in reference [2]
are the negative of the ones used by this actor, which
correspond to the definition in most other texts,
and to the definition of partial-correlation (PARCOR)
coefficients in the statistics literature.
The signs of the coefficients used in this actor are appropriate for values
given by the LevinsonDurbin actor.
<p>
<b>References</b>
<p>[1]
J. Makhoul, "Linear Prediction: A Tutorial Review",
<i>Proc. IEEE</i>, Vol. 63, pp. 561-580, Apr. 1975.
<p>[2]
S. M. Kay, <i>Modern Spectral Estimation: Theory & Application</i>,
Prentice-Hall, Englewood Cliffs, NJ, 1988.

@see ptolemy.domains.sdf.lib.FIR
@see LevinsonDurbin
@see RecursiveLattice
@see ptolemy.domains.sdf.lib.VariableLattice
@author Edward A. Lee, Christopher Hylands
@version $Id$
@since Ptolemy II 1.0
*/

public class Lattice extends Transformer {

    /** Construct an actor with the given container and name.
     *  @param container The container.
     *  @param name The name of this actor.
     *  @exception IllegalActionException If the actor cannot be contained
     *   by the proposed container.
     *  @exception NameDuplicationException If the container already has an
     *   actor with this name.
     */
    public Lattice(CompositeEntity container, String name)
            throws NameDuplicationException, IllegalActionException  {
        super(container, name);

	input.setTypeEquals(BaseType.DOUBLE);
	output.setTypeEquals(BaseType.DOUBLE);

        reflectionCoefficients = new Parameter(this, "reflectionCoefficients");
        // Note that setExpression() will call attributeChanged().
        reflectionCoefficients.setExpression(
                "{0.804534, -0.820577, 0.521934, -0.205}");
    }

    ///////////////////////////////////////////////////////////////////
    ////                         parameters                        ////

    /** The reflection coefficients.  This is an array of doubles with
     *  default value {0.804534, -0.820577, 0.521934, -0.205}. These
     *  are the reflection coefficients for the linear predictor of a
     *  particular random process.
     */
    public Parameter reflectionCoefficients;

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////

    /** If the argument is the <i>reflectionCoefficients</i> parameter,
     *  then reallocate the arrays to use.
     *  @param attribute The attribute that changed.
     *  @exception IllegalActionException If the base class throws it.
     */
    public void attributeChanged(Attribute attribute)
            throws IllegalActionException {
        if (attribute == reflectionCoefficients) {
            ArrayToken value = (ArrayToken)reflectionCoefficients.getToken();
            int valueLength = value.length();
            if (_backward == null || valueLength != _backward.length) {
                // Need to reallocate the arrays.
                _backward = new double[valueLength];
                _backwardCache = new double[valueLength];
                _forward = new double[valueLength + 1];
                _forwardCache = new double[valueLength + 1];
                _reflectionCoefs = new double[valueLength];
            }
            for (int i = 0; i < valueLength; i++) {
                _reflectionCoefs[i] =
                    ((DoubleToken)value.getElement(i)).doubleValue();
            }
        } else {
            super.attributeChanged(attribute);
        }
    }

    /** Consume one input token, if there is one, and produce one output
     *  token.  If there is no input, the produce no output.
     *  @exception IllegalActionException If there is no director.
     */
    public void fire() throws IllegalActionException {
        if (input.hasToken(0)) {
            DoubleToken in = (DoubleToken)input.get(0);
            // NOTE: The following code is ported from Ptolemy Classic.
            double k;
            int M = _backward.length;
            // Forward prediction error
            _forwardCache[0] = in.doubleValue();   // _forwardCache(0) = x(n)
            for (int i = 1; i <= M; i++) {
                k = - _reflectionCoefs[i-1];
                _forwardCache[i] = k * _backwardCache[i-1] + _forwardCache[i-1];
            }
            output.broadcast(new DoubleToken(_forwardCache[M]));

            // Backward:  Compute the weights for the next round
            for (int i = M-1; i >0 ; i--) {
                k = - _reflectionCoefs[i-1];
                _backwardCache[i] = k * _forwardCache[i-1]
                    + _backwardCache[i-1];
            }
            _backwardCache[0] = _forwardCache[0];   // _backwardCache[0] = x[n]
        }
    }

    /** Update the backward and forward prediction errors that
     *  were generated in fire() method.
     *  @return False if the number of iterations matches the number requested.
     *  @exception IllegalActionException If there is no director.
     */
    public boolean postfire() throws IllegalActionException {
        System.arraycopy(_backwardCache, 0,
                        _backward, 0,
                        _backwardCache.length);
        System.arraycopy(_forwardCache, 0,
                        _forward, 0,
                        _forwardCache.length);
        return super.postfire();
    }

    ///////////////////////////////////////////////////////////////////
    ////                       private variables                   ////

    // Backward prediction errors.
    private double[] _backward = null;

    // Cache of backward prediction errors.
    // The fire() method updates _forwardCache and postfire()
    // copies _forwardCache to _forward so this actor will work in domains
    // like SR.
    private double[] _backwardCache = null;

    // Forward prediction errors.
    private double[] _forward = null;

    // Cache of forward prediction errors.
    // The fire() method updates _forwardCache and postfire()
    // copies _forwardCache to _forward so this actor will work in domains
    // like SR.
    private double[] _forwardCache = null;

    // Cache of reflection coefficients.
    private double[] _reflectionCoefs = null;
}
