/**
  * Copyright: [2011] Ben Lever
  */
package com.nicta.scoobi

import org.apache.hadoop.io._
import scala.collection.mutable.{Map => MMap}
import javassist._


/** The super-class of all "value" types used in Hadoop jobs. */
abstract class ScoobiWritable[A](private var x: A) extends Writable { self =>
  def this() = this(null.asInstanceOf[A])
  def get: A = x
  def set(x: A) = { self.x = x }
}


/** Constructs a subclass of ScoobiWritable dynamically. */
object ScoobiWritable {

  val builtClasses: MMap[String, RuntimeClass] = MMap.empty

  def apply(name: String, m: Manifest[_], wt: HadoopWritable[_]): RuntimeClass = {
    if (!builtClasses.contains(name)) {
      val builder = new ScoobiWritableClassBuilder(name, m, wt)
      builtClasses += (name -> builder.toRuntimeClass())
    }

    builtClasses(name)
  }

  def apply[A](name: String, witness: A)(implicit m: Manifest[A], wt: HadoopWritable[A]): RuntimeClass = {
    apply(name, m, wt)
  }
}


/** A ScoobiWritable subclass is constructed based on a HadoopWritable typeclass
  * model imiplicit parameter. Using this model object, the Hadoop Writable methods
  * 'write' and 'readFields' can be generated. */
class ScoobiWritableClassBuilder(name: String, m: Manifest[_], wt: HadoopWritable[_]) extends ClassBuilder {

  def className = name

  def extendClass: Class[_] = classOf[ScoobiWritable[_]]

  def build = {
    /* Deal with HadoopWritable type class. */
    addTypeClassModel(wt, "writer")

    /* 'write' - method to override from Writable */
    val writeMethod = CtNewMethod.make(CtClass.voidType,
                                       "write",
                                       Array(pool.get("java.io.DataOutput")),
                                       Array(),
                                       "writer.toWire(" + toObject("get()", m) + ", $1);",
                                       ctClass)
    ctClass.addMethod(writeMethod)

    /* 'readFields' = method to override from Writable */
    val readFieldsMethod = CtNewMethod.make(CtClass.voidType,
                                            "readFields",
                                            Array(pool.get("java.io.DataInput")),
                                            Array(),
                                            "set(" + fromObject("writer.fromWire($1)", m) + ");",
                                            ctClass)
    ctClass.addMethod(readFieldsMethod)

    /* 'toString' = method to override from Writable */
    val toStringMethod = CtNewMethod.make(pool.get("java.lang.String"),
                                          "toString",
                                          Array(),
                                          Array(),
                                          "return writer.show(" + toObject("get()", m) + ");",
                                          ctClass)
    ctClass.addMethod(toStringMethod)
  }
}