import scala.util.Try
import gremlin.scala._
import io.shiftleft.codepropertygraph.generated._
import java.nio.file.Paths
import io.shiftleft.queryprimitives.utils.ExpandTo
import org.apache.tinkerpop.gremlin.structure.Direction
import io.shiftleft.queryprimitives.steps.Implicits.JavaIteratorDeco
import javax.script.ScriptEngineManager
import scala.io.Source

/* APACHE from https://github.com/shiftleftsecurity/joern/ */
/* TODO: license properly */


/** Some helper functions: adapted from ReachingDefPass.scala in codeproperty graph repo */
def vertexToStr(vertex: Vertex, identifiers: Map[Vertex,Int]): String = {
  val str = new StringBuffer()

  str.append("id_")
  str.append(identifiers(vertex).toString + "_")

  str.append("f_")
  Try {
    val methodVertex = vertex.vertices(Direction.IN, "CONTAINS").nextChecked
    val fileName = methodVertex.vertices(Direction.IN, "CONTAINS").nextChecked match {
      case file: nodes.File => file.asInstanceOf[nodes.File].name
      case _ => "NA"
    }
    val filename_temp = Paths.get(fileName).getFileName.toString
    val newfile_name = filename_temp.replaceAll("CWE.*__CWE[0-9]+_", "").replaceAll("\\.", "_")

    str.append(newfile_name + "_")
  }

  str.append("l_")
  Try {
    str.append(vertex.value2(NodeKeys.LINE_NUMBER).toString + "_")
  }

  str.append("c_")
  Try {
    str.append(vertex.value2(NodeKeys.COLUMN_NUMBER).toString + "_")
  }

  str.toString
}

def toProlog(graph: ScalaGraph): String = {
  var vertex_identifiers:Map[Vertex,Int] = Map()

  var index = 0
  graph.V.l.foreach{ v =>
    vertex_identifiers += (v -> index)
    index += 1
  }

  val buf = new StringBuffer()

  buf.append("% START: Generated Prolog\n")

  buf.append("% NODE PROPERTIES \n")
  graph.V.l.foreach{ v =>
    val vertex_str = vertexToStr(v, vertex_identifiers)

    Try {
      val node_name = v.value2(NodeKeys.NAME).toString

      node_name match {
        case "ALLOCA" =>
          buf.append("alloc(" + vertex_str + ").\n")
        case "malloc" =>
          buf.append("malloc(" + vertex_str + ").\n")
        case "memcopy" =>
          buf.append("writeToPointer(" + vertex_str + ").\n")
        case "memmove" =>
          buf.append("writeToPointer(" + vertex_str + ").\n")
        case "<operator>.sizeOf" =>
          buf.append("sizeOf(" + vertex_str + ").\n")
        case "<operator>.assignment" =>
          buf.append("assignment(" + vertex_str + ").\n")
        case "<operator>.computedMemberAccess" =>
          buf.append("compMemberAccess(" + vertex_str + ").\n")
      }
    }
  }

  buf.append("% CODE\n")
  graph.V.l.foreach{ v =>
    Try {
      buf.append(
        "source_code("
          + vertexToStr(v, vertex_identifiers)
          + ", \""
          + v.value2(NodeKeys.CODE).toString
          + "\")."
          + "\n"
      )
    }
  }

  buf.append("% AST\n")
  graph.E.hasLabel("AST").l.foreach { e =>
    val parentVertex = vertexToStr(e.outVertex, vertex_identifiers).replace("\"","\'")
    val childVertex = vertexToStr(e.inVertex, vertex_identifiers).replace("\"","\'")
    buf.append(s"""ast($parentVertex, $childVertex).\n """)
  }

  buf.append("% CFG\n")
  graph.E.hasLabel("CFG").l.foreach { e =>
    val parentVertex = vertexToStr(e.outVertex, vertex_identifiers).replace("\"","\'")
    val childVertex = vertexToStr(e.inVertex, vertex_identifiers).replace("\"","\'")
    buf.append(s"""cfg($parentVertex, $childVertex).\n """)
  }

  buf.append("% END: Generated Prolog ")

  buf.toString
}

toProlog(cpg.graph)
