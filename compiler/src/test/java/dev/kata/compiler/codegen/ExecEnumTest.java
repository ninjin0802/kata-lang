package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import kata.rt.Handler;
import kata.rt.KList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecEnumTest {
    static final String SOURCE = """
            enum Shape {
              Dot,
              Circle { radius: Int },
              Box { width: Int, height: Float, label: String, solid: Bool },
            }
            enum Tree {
              Leaf { value: Int },
              Node { left: Tree, right: Tree },
            }
            class E {
              var last: Shape = Shape.Dot;
              fn dot(self) -> Shape {
                return Shape.Dot;
              }
              fn circle(self, radius: Int) -> Shape {
                return Shape.Circle { radius: radius };
              }
              fn box(self) -> Shape {
                return Shape.Box { width: 2, height: 1.5, label: "b", solid: true };
              }
              fn describe(self, shape: Shape) -> String {
                match (shape) {
                  Shape.Dot => {
                    return "dot";
                  }
                  Shape.Circle { radius } => {
                    return "circle ".concat(other: radius.toString());
                  }
                  Shape.Box { width, height, label, solid } => {
                    if (solid) {
                      return label.concat(other: width.toString()).concat(other: height.toString());
                    }
                    return "hollow";
                  }
                }
              }
              fn count(self, shapes: List<Shape>) -> Int {
                var circles: Int = 0;
                for (shape: Shape in shapes) {
                  match (shape) {
                    Shape.Dot => {
                    }
                    Shape.Circle { radius } => {
                      circles = circles + radius;
                    }
                    Shape.Box { width, height, label, solid } => {
                      circles = circles + 100;
                    }
                  }
                }
                return circles;
              }
              fn sum(self, tree: Tree) -> Int {
                match (tree) {
                  Tree.Leaf { value } => {
                    return value;
                  }
                  Tree.Node { left, right } => {
                    return self.sum(tree: left) + self.sum(tree: right);
                  }
                }
              }
              fn sample(self) -> Tree {
                return Tree.Node {
                  left: Tree.Leaf { value: 1 },
                  right: Tree.Node { left: Tree.Leaf { value: 2 }, right: Tree.Leaf { value: 3 } },
                };
              }
              fn remember(self, shape: Shape) -> Unit {
                self.last = shape;
              }
              fn setter(self) -> Handler<Shape> {
                return self.remember;
              }
              fn lastName(self) -> String {
                return self.describe(shape: self.last);
              }
            }
            """;

    static Object e;

    @BeforeAll
    static void compile() throws Exception {
        e = create(ExecSupport.compile(SOURCE), "app.main.E");
    }

    @Test
    void matchSelectsTheVariantAndBindsItsFields() throws Throwable {
        assertEquals("dot", call(e, "describe", call(e, "dot")));
        assertEquals("circle 7", call(e, "describe", call(e, "circle", 7L)));
        assertEquals("b21.5", call(e, "describe", call(e, "box")));
    }

    @Test
    void matchWorksAsAStatementInsideLoops() throws Throwable {
        KList shapes = KList.of(new Object[] {call(e, "dot"), call(e, "circle", 3L), call(e, "circle", 4L),
            call(e, "box")});
        assertEquals(107L, call(e, "count", shapes));
    }

    @Test
    void enumsCanBeRecursive() throws Throwable {
        assertEquals(6L, call(e, "sum", call(e, "sample")));
    }

    @Test
    void enumsWorkAsFieldsAndHandlerArguments() throws Throwable {
        assertEquals("dot", call(e, "lastName"));
        ((Handler) call(e, "setter")).run(call(e, "circle", 2L));
        assertEquals("circle 2", call(e, "lastName"));
        call(e, "remember", call(e, "dot"));
    }

    @Test
    void variantsAreClassesNestedUnderTheEnum() throws Throwable {
        Object circle = call(e, "circle", 1L);
        assertEquals("app.main.Shape$Circle", circle.getClass().getName());
        assertEquals("app.main.Shape", circle.getClass().getSuperclass().getName());
        assertNotSame(call(e, "dot"), null);
    }
}
