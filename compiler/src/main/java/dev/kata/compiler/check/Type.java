package dev.kata.compiler.check;

/** A kata type (spec chapter 04). */
public sealed interface Type {
    Type INT = new Prim(Prim.Kind.INT);
    Type FLOAT = new Prim(Prim.Kind.FLOAT);
    Type BOOL = new Prim(Prim.Kind.BOOL);
    Type STRING = new Prim(Prim.Kind.STRING);
    Type UNIT = new Prim(Prim.Kind.UNIT);
    Type ACTION = new Prim(Prim.Kind.ACTION);
    Type UNKNOWN = new Unknown();

    /** The type as written in kata source. */
    String display();

    record Prim(Kind kind) implements Type {
        public enum Kind {
            INT("Int"), FLOAT("Float"), BOOL("Bool"), STRING("String"), UNIT("Unit"), ACTION("Action");

            final String display;

            Kind(String display) {
                this.display = display;
            }
        }

        @Override
        public String display() {
            return kind.display;
        }
    }

    record ListType(Type elem) implements Type {
        @Override
        public String display() {
            return "List<" + elem.display() + ">";
        }
    }

    record HandlerType(Type arg) implements Type {
        @Override
        public String display() {
            return "Handler<" + arg.display() + ">";
        }
    }

    record ClassType(String qualifiedName) implements Type {
        @Override
        public String display() {
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
    }

    record EnumType(String qualifiedName) implements Type {
        @Override
        public String display() {
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
    }

    record InterfaceType(String qualifiedName) implements Type {
        @Override
        public String display() {
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
    }

    /** The type of an expression that already has a diagnostic; it is compatible with everything. */
    record Unknown() implements Type {
        @Override
        public String display() {
            return "?";
        }
    }
}
