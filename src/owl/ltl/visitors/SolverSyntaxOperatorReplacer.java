package owl.ltl.visitors;

import owl.ltl.*;

public class SolverSyntaxOperatorReplacer implements Visitor<Formula> {

    @Override
    public Formula apply(Formula formula) {
        return formula.accept(this);
    }

    @Override
    public Formula visit(Biconditional biconditional) {
        Formula left = biconditional.left.accept(this);
        Formula right = biconditional.right.accept(this);
        return Conjunction.of(Disjunction.of(left.not(), right), Disjunction.of(left, right.not()));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
        return Conjunction.of(conjunction.children.stream().map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
        return Disjunction.of(disjunction.children.stream().map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
        Formula operand = fOperator.operand;
        return UOperator.of(BooleanConstant.TRUE, operand);
    }

    @Override
    public Formula visit(FrequencyG freq) {
        Formula operand = freq.operand.accept(this);
        return FrequencyG.of(operand);
    }

    @Override
    public Formula visit(GOperator gOperator) {
        Formula operand = gOperator.operand;
        return UOperator.of(BooleanConstant.TRUE, operand.not()).not();
    }

    @Override
    public Formula visit(HOperator hOperator) {
        Formula operand = hOperator.operand.accept(this);
        return HOperator.of(operand);
    }

    @Override
    public Formula visit(Literal literal) {
        return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
        // p M q" -> "q U (p & q)
        Formula left = mOperator.left.accept(this);
        Formula right = mOperator.right.accept(this);

        return UOperator.of(right, Conjunction.of(right, left));
    }

    @Override
    public Formula visit(OOperator oOperator) {
        Formula operand = oOperator.operand.accept(this);
        return OOperator.of(operand);
    }

    @Override
    public Formula visit(ROperator rOperator) {
        // p R q" <-> q w (q && p) <-> (q U (q && p)) || G q
        Formula left = rOperator.left.accept(this);
        Formula right = rOperator.right.accept(this);
        Formula uOperator = UOperator.of(right, Conjunction.of(right, left));
        Formula gOperator = UOperator.of(BooleanConstant.TRUE, right.not()).not();
        return Disjunction.of(uOperator, gOperator);
    }

    @Override
    public Formula visit(SOperator sOperator) {
        Formula left = sOperator.left.accept(this);
        Formula right = sOperator.right.accept(this);

        return SOperator.of(left, right);
    }

    @Override
    public Formula visit(TOperator tOperator) {
        Formula left = tOperator.left.accept(this);
        Formula right = tOperator.right.accept(this);

        return TOperator.of(left, right);
    }

    @Override
    public Formula visit(UOperator uOperator) {
        Formula left = uOperator.left.accept(this);
        Formula right = uOperator.right.accept(this);
        return UOperator.of(left, right);
    }

    @Override
    public Formula visit(WOperator wOperator) {
        Formula left = wOperator.left.accept(this);
        Formula right = wOperator.right.accept(this);
        Formula gOperator = UOperator.of(BooleanConstant.TRUE, left.not()).not();
        return Disjunction.of(gOperator, UOperator.of(left, right));
    }

    @Override
    public Formula visit(XOperator xOperator) {
        Formula operand = xOperator.operand.accept(this);
        return XOperator.of(operand);
    }

    @Override
    public Formula visit(YOperator yOperator) {

        Formula operand = yOperator.operand.accept(this);
        return YOperator.of(operand);
    }

    @Override
    public Formula visit(ZOperator zOperator) {
        Formula operand = zOperator.operand.accept(this);
        return ZOperator.of(operand);
    }

}
