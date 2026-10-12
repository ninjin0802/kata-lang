package dev.kata.compiler.check;

import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;

/** What a member access or call resolved to. */
public sealed interface MemberRef {
    /** A field of a class. */
    record FieldRef(String owner, FieldInfo field) implements MemberRef {}

    /** A method of a class or interface, identified by the owner's qualified name. */
    record MethodRef(String owner, boolean ownerIsInterface, MethodInfo method) implements MemberRef {}

    /** A built-in method of Int, String or List. */
    record BuiltinMethod(Type receiver, MethodInfo method) implements MemberRef {}
}
