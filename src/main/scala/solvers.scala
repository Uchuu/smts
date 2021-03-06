/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *\
 *  This file is part of Smts.                                               *
 *                                                                           *
 *  Smts is free software: you can redistribute it and/or modify             *
 *  it under the terms of the GNU Lesser General Public License as           *
 *  published by the Free Software Foundation, either version 3 of the       *
 *  License, or (at your option) any later version.                          *
 *                                                                           *
 *  Smts is distributed in the hope that it will be useful,                  *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of           *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
 *  GNU Lesser General Public License for more details.                      *
 *                                                                           *
 *  You should have received a copy of the GNU Lesser General Public         *
 *  License along with Smts.                                                 *
 *  If not, see <http://www.gnu.org/licenses/>.                              *
\* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

package smts

import scala.util.parsing.combinator.RegexParsers

import akka.actor._

/** Trait gathering the solver information for the underlying solvers. */
trait SmtsSolvers[Expr,Ident,Sort]
extends SmtsCore[Expr,Ident,Sort]
with SmtsPrinters[Expr,Ident,Sort]
with SmtsLog[Expr,Ident,Sort]
with SmtsParsers[Expr,Ident,Sort] {

  // |=====| Solver info.

  /** Extended by all the solver classes. */
  sealed trait SolverInfo extends SmtLibParsers {
    import Messages.{ToSmtsMsg,SetOption}

    /** The root of the command to use to launch the solver. */
    val baseCommand: String
    /** The command launching the solver. */
    val command: String
    /** User defined options for the underlying solver. */
    val options: String
    /** If true Smts will parse for success (default '''false'''). */
    val success: Boolean
    /** If true model generation will be activated in the solver
      * (default '''false'''). */
    val models: Boolean
    /** If true unsat core generation will be activated in the solver
      * (default '''false'''). */
    val unsatCores: Boolean
    /** Allows to specify a timeout in milliseconds (default '''None'''). */
    val timeout: Option[Int]
    /** Allows to specify a timeout in milliseconds for each query to the solver
      * (default '''None'''. */
    val timeoutQuery: Option[Int]
    /** Commands the solver will always be launched with (default '''Nil'''). */
    val initWith: List[ToSmtsMsg]

    /** Standard smts name of the solver. */
    val name: String

    /** Commands to write when launching the solver. */
    val startMsgs: List[ToSmtsMsg]

    def toStringList = {
      ("name:         " + name) ::
      ("baseCommand:  " + baseCommand) ::
      ("options:      " + options) ::
      ("success:      " + success) ::
      ("models:       " + models) ::
      ("unsatCores:   " + unsatCores) ::
      ("timeout:      " + timeout) ::
      ("timeoutQuery: " + timeoutQuery) ::
      ("initWith:     " + initWith.map(SolverInfo.msgToString(_))) ::
      ("command:      " + command) :: Nil
    }

    /** Converts an integer in milliseconds to its string representation in
      * seconds. */
    def millis2secs(millis: Int) = {
      val milliString = millis.toString
      milliString.size match {
        case n if n > 3 => milliString.take(n-3)
        case n => "0." + ("0" * (n-3)) + milliString
      }
    }

    /** Clones the current solver, changing only the values for which
      *  the corresponding argument is not '''None'''. */
    def change(
      newSuccess: Option[Boolean] = None,
      newModels: Option[Boolean] = None,
      newUnsatCores: Option[Boolean] = None,
      newBaseCommand: Option[String] = None,
      newInitWith: Option[List[Messages.ToSmtsMsg]] = None,
      newTimeout: Option[Option[Int]] = None,
      newTimeoutQuery: Option[Option[Int]] = None,
      newOptions: Option[String] = None
    ) = this match {
      case _: Z3       => Z3(
        newSuccess getOrElse success,
        newModels getOrElse models,
        newUnsatCores getOrElse unsatCores,
        newBaseCommand getOrElse baseCommand,
        newInitWith getOrElse initWith,
        newTimeout getOrElse timeout,
        newTimeoutQuery getOrElse timeoutQuery,
        newOptions getOrElse options
      )
      case _: MathSat5 => MathSat5(
        newSuccess getOrElse success,
        newModels getOrElse models,
        newUnsatCores getOrElse unsatCores,
        newBaseCommand getOrElse baseCommand,
        newInitWith getOrElse initWith,
        newTimeout getOrElse timeout,
        newTimeoutQuery getOrElse timeoutQuery,
        newOptions getOrElse options
      )
      case _: CVC4     => CVC4(
        newSuccess getOrElse success,
        newModels getOrElse models,
        newUnsatCores getOrElse unsatCores,
        newBaseCommand getOrElse baseCommand,
        newInitWith getOrElse initWith,
        newTimeout getOrElse timeout,
        newTimeoutQuery getOrElse timeoutQuery,
        newOptions getOrElse options
      )
      case _: DReal      => DReal(
        newBaseCommand getOrElse baseCommand,
        newInitWith getOrElse initWith,
        newOptions getOrElse options
      )
    }
  }

  /** Solver information class for Z3. */
  case class Z3(
    val success: Boolean = false,
    val models: Boolean = false,
    val unsatCores: Boolean = false,
    val baseCommand: String = "z3 -in -smt2",
    val initWith: List[Messages.ToSmtsMsg] = Nil,
    val timeout: Option[Int] = None,
    val timeoutQuery: Option[Int] = None,
    val options: String = ""
  ) extends SolverInfo {
    import Messages._
    val startMsgs: List[ToSmtsMsg] = {
      val withCores =
        if (unsatCores) SetOption(":produce-unsat-cores true") :: initWith
        else initWith
      val withModels =
        if (models) SetOption(":produce-models true") :: withCores
        else withCores
      if (success) SetOption(":print-success true") :: withModels else withModels
    }
    val command = baseCommand + ( timeout match {
      case None => ""
      case Some(to) => " -T:" + millis2secs(to)
    }) + (timeoutQuery match {
      case None => ""
      case Some(to) => " -t:" + millis2secs(to)
    }) + " " + options
    val name = "z3"
    override def toString = name
  }

  /** Solver information class for mathsat. */
  case class MathSat5(
    val success: Boolean = false,
    val models: Boolean = false,
    val unsatCores: Boolean = false,
    val baseCommand: String = "mathsat",
    val initWith: List[Messages.ToSmtsMsg] = Nil,
    val timeout: Option[Int] = None,
    val timeoutQuery: Option[Int] = None,
    val options: String = ""
  ) extends SolverInfo {
    import Messages._
    val startMsgs: List[ToSmtsMsg] = {
      val withCores =
        if (unsatCores) SetOption(":produce-unsat-cores true") :: initWith
        else initWith
      val withModels =
        if (models) SetOption(":produce-models true") :: withCores
        else withCores
      if (success) SetOption(":print-success true") :: withModels else withModels
    }
    val command = baseCommand + " " + options
    val name = "mathsat5"
    override def toString = name

    /** MathSat has a different convention for get-model answers. */
    override lazy val modelParser: PackratParser[Messages.Model] =
      "(" ~> rep(defineParser) <~ ")" ^^ {
        case funs => Messages.Model(funs)
      }

    /** MathSat has a different convention for model definition. */
    override lazy val defineParser: PackratParser[Binding] =
      "(" ~> smt2Ident ~ smt2Expr <~ ")" ^^ {
        case id ~ expr => UntypedBinding(id,expr)
      }
  }

  /** Solver information class for CVC4. Unsat cores deactivated. */
  case class CVC4(
    val success: Boolean = false,
    val models: Boolean = false,
    val unsatCores: Boolean = false,
    val baseCommand: String = "cvc4 -q --lang=smt",
    val initWith: List[Messages.ToSmtsMsg] = Nil,
    val timeout: Option[Int] = None,
    val timeoutQuery: Option[Int] = None,
    val options: String = ""
  ) extends SolverInfo {
    import Messages._
    val startMsgs: List[ToSmtsMsg] = {
      val withModels =
        if (models) SetOption(":produce-models true") :: initWith
        else initWith
      if (success) SetOption(":print-success true") :: withModels else withModels
    }
    val command = baseCommand + ( timeout match {
      case None => ""
      case Some(to) => " --tlimit=" + to
    }) + (timeoutQuery match {
      case None => ""
      case Some(to) => " --tlimit-per=" + to
    }) + " " + options
    val name = "cvc4"
    override def toString = name
  }


  /** Solver information class for dReal. */
  class DReal private(
    val baseCommand: String = "dReal",
    val initWith: List[Messages.ToSmtsMsg] = Nil,
    val options: String = ""
  ) extends SolverInfo {
    // Does not apply to dReal.
    val success = false
    val models = false
    val unsatCores = false
    val timeout = None
    val timeoutQuery = None

    val command = baseCommand + " " + options
    val name = "dReal"
    val startMsgs = initWith

    override def toStringList = {
      ("name:         " + name) ::
      ("baseCommand:  " + baseCommand) ::
      ("options:      " + options) ::
      ("initWith:     " + initWith.map(SolverInfo.msgToString(_))) ::
      ("command:      " + command) :: Nil
    }
  }

  object DReal {

    def apply(
      baseCommand: String = "dReal",
      initWith: List[Messages.ToSmtsMsg] = Nil,
      options: String = ""
    ) = new DReal(baseCommand,initWith,options)
    def unapply(that: DReal) = Some((that.baseCommand,that.initWith,that.options))

    /** Defining the whole dReal actor here. */
    class Master private[smts] (
      val client: ActorRef, val solverInfo: SolverInfo, val log: Option[String]
    ) extends SmtLibPrinters with Actor {
      import Messages._
      import java.io._

      /** Initializes the solver by sending the start messages. */
      protected def initSolver = solverInfo.startMsgs foreach (msg => {
        writeMsg(msg,sw) ; reader ! msg
      })

      override def preStart = initSolver
      override def postStop = { context stop reader ; solverProcess.destroy }

      protected val reader = context.actorOf(Props(log match {
        case None => new DReader(self,solverInfo)
        case Some(path) => new DReader(self,solverInfo) with ReaderLog {
          def logFilePath = path
        }
      }), name = "reader")

      /** Creates a new solver process. */
      protected def createSolverProcess = (new ProcessBuilder(
        solverInfo.command.split(" "): _*
      )).redirectErrorStream(true).start

      /** The solver process. */
      protected var solverProcess = createSolverProcess
      /** Returns a '''BufferedWriter''' on the solver process. */
      protected def getSolverWriter = new BufferedWriter(
        new OutputStreamWriter(solverProcess.getOutputStream)
      )
      /** Returns a '''BufferedReader''' on the solver process. */
      protected def getSolverReader = new BufferedReader(
        new InputStreamReader(solverProcess.getInputStream)
      )
      /** Actual writer on the solver process input. */
      protected var _solverWriter = getSolverWriter
      protected def solverWriter = _solverWriter
      /** Actual reader on the solver process input. */
      protected var _solverReader = getSolverReader
      protected def solverReader = _solverReader

      protected var sw = new StringWriter()
      protected def reset = {
        sw = new StringWriter()
        solverProcess = createSolverProcess
        _solverWriter = getSolverWriter
        _solverReader = getSolverReader
        initSolver
      }

      def receive = {
        case msg => handleMsg(msg)
      }

      def handleMsg(msg: Any): Unit = msg match {
        case Script(msgs) => msgs foreach (m => handleMsg(m))
        case Restart => reset
        case ExitSolver => {
          reader ! ExitSolver
          sw write "(exit)\n"
          solverWriter write sw.toString
          solverWriter.close
          reader ! solverReader
          reset
        }
        case msg: ToSmtsMsg => { reader ! msg ; writeMsg(msg,sw) }
        case msg: FromSmtsMsg => client ! msg
        case msg => throw new UnexpectedMessageException(msg)
      }

    }

    private[smts] class DReader(
      val master: ActorRef, val solverInfo: SolverInfo
    ) extends Actor with LogFunctions {
      import Messages._

      override def preStart = {
        logResultLine("Solver configuration:")
        solverInfo.toStringList.foreach(line => logResultLine("  " + line))
        logSpace
        logSpace
        logResultLine("|=======| Starting |=======|")
        logSpace
      }

      def receive = {
        case br: java.io.BufferedReader => {
          def loop(line: String): String =
            if (line == null || line.trim == "") loop(br.readLine) else line
          val line = loop("")
          line match {
            case "sat" => master ! Sat
            case "unsat" => master ! Unsat
            case "unknown" => master ! Unknown
            case line => master ! SolverError("Unexpected dReal answer:" :: line :: Nil)
          }
          logResultLine(line)
          logSpace ; logSpace
          logResultLine("|=======| RESTART |=======|")
          logSpace
        }
        case msg: ToSmtsMsg => logMsg(msg: ToSmtsMsg)
      }

    }
  }


  /** Solver information object, can load a configuration file and
    * create user specified configurations. */
  object SolverInfo extends RegexParsers with SmtLibPrinters {
    import scala.collection.mutable.HashMap
    /** Stores the mappings from user defined identifiers to solver infos. */
    private val idMap = new HashMap[String,SolverInfo]

    def addMapping(key: String, solver: SolverInfo) = idMap update (key,solver)

    def apply(
      identifier: String,
      success: Option[Boolean] = None,
      models: Option[Boolean] = None,
      unsatCores: Option[Boolean] = None,
      baseCommand: Option[String] = None,
      initWith: Option[List[Messages.ToSmtsMsg]] = None,
      timeout: Option[Option[Int]] = None,
      timeoutQuery: Option[Option[Int]] = None,
      options: Option[String] = None
    ) = getSolver(identifier) match {
      case Some(solverInfo) => solverInfo.change(
        success,models,unsatCores,baseCommand,initWith,timeout,timeoutQuery,options
      )
      case None => throw new ConfigurationException(
        "Could not create solver information, identifier \"" + identifier +
        "\" is undefined."
      )
    }

    protected def getSolver(key: String) = key match {
      case "Z3" | "z3" => Some(Z3())
      case "MathSat5" | "mathsat5" | "MathSAT5" | "Mathsat5" |
           "MathSat"  | "mathsat"  | "MathSAT"  | "Mathsat" => Some(MathSat5())
      case "CVC4" | "cvc4" => Some(CVC4())
      case _ => idMap get key
    }

    def load(file: String) = {
      println("Loading configuration file.")
      if (new java.io.File(file).exists) {
        println("Configuration file exists.")
        val lines = scala.io.Source.fromFile(file).mkString
        println("Parsing configuration file.")
        parseAll(configParser,lines) match {
          case Success(_,_) => println("Done loading.")
          case NoSuccess(_,next) => {
            println("Parsing error.")
            println
            println(next.pos.longString)
          }
        }
      } else throw new ConfigurationLoadingException(
        "file \"" + file + "\" does not exist."
      )
    }

    protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/)+""".r

    private def configParser: Parser[Unit] = rep(entryParser) ^^ { case _ => () }

    private def entryParser: Parser[Unit] = {
      // Actual parser with all the options.
      identParser ~ ("extends" ~> identParser) ~ (
        "{" ~>
        opt("success" ~ "=" ~> boolParser) ~
        opt("models" ~ "=" ~> boolParser) ~
        opt("unsatCores" ~ "=" ~> boolParser) ~
        opt("baseCommand" ~ "=" ~> parenedStringParser) ~
        opt("timeout" ~ "=" ~> toParser) ~
        opt("timeoutQuery" ~ "=" ~> toParser) ~
        opt("options" ~ "=" ~> parenedStringParser) <~
        "}"
      ) ^^ {
        case id ~ father ~ (suc ~ mods ~ cores ~ base ~ to ~ toQ ~ opts) =>
          // Making sure '''id''' is not already used.
          if (idMap contains id) throw new ConfigurationLoadingException(
            "identifier \"" + id + "\" is already defined."
          ) else getSolver(father) match {
            // Making sure '''father''' is defined.
            case Some(solver) => addMapping(id, solver.change(
              newSuccess = suc, newModels = mods, newUnsatCores = cores,
              newBaseCommand = base, newTimeout = to, newTimeoutQuery = toQ,
              newOptions = opts
            ))
            case None => throw new ConfigurationLoadingException(
              "\"" + id + "\" extends an unknown \"" + father + "\" solver info."
            )
          }
        } |
      // Alias parser.
      identParser ~ ("extends" ~> identParser) ^^ {
        case id ~ father =>
          // Making sure '''id''' is not already used.
          if (idMap contains id) throw new ConfigurationLoadingException(
            "identifier \"" + id + "\" is already defined."
          ) else getSolver(father) match {
            // Making sure '''father''' is defined.
            case Some(solver) => addMapping(id, solver)
            case None => throw new ConfigurationLoadingException(
              "\"" + id + "\" extends an unknown \"" + father + "\" solver info."
            )
          }
        }
      }

    private def toParser: Parser[Option[Int]] =
      intParser ^^ { case n => if (n < 0) None else Some(n) }
    private def parenedStringParser: Parser[String] = "(" ~> """[^\)]*""".r <~ ")"
    private def identParser: Parser[String] = """[a-zA-Z][a-zA-Z0-9\.\-]*""".r
    private def intParser: Parser[Int] = {
      "0" ^^ { case _ => 0 } |
      """[1-9][0-9]*""".r ^^ { case s => s.toInt }
    }
    private def boolParser: Parser[Boolean] = {
      "true" ^^ { case _ => true } | "false" ^^ { case _ => false }
    }
  }


  /** Exception thrown in case of a configuration error. */
  class ConfigurationException(val msg: String) extends Exception(msg)
  class ConfigurationLoadingException(val message: String) extends ConfigurationException(
    "Error while loading the configuration file: " + message
  )
}
