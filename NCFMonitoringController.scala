package controllers

import javax.inject.Inject
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.mvc.Result
import play.api.libs.json.{JsString, Json}
import com.typesafe.config._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import play.api.http.{Status => StatusCodes}
import scala.sys.process._
import play.api.Play
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import java.net.NetworkInterface
import scala.collection.JavaConversions._
import play.api.data._
import scala.language.postfixOps
import reactivemongo.api.MongoDriver
import play.api.libs.ws._
import play.api.libs.json._
import play.api.Configuration

class NCFMonitoringController  @Inject() (ws: WSClient, implicit val config: Configuration)extends Controller {
  
/***************************Gestion des Machines******************************/  
  
def listerMachines() = Action.async { 
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url(listen_fleet+"/fleet/v1/machines").get().map{ response => Ok(Json.toJson(response.body))}	
} 
 
def logsMachines()=Action {
  val chemin_ssh=config.underlying.getString("ssh")
  val machines=config.underlying.getObject("machines")
  val userMachine = new Array[String](machines.size())
  val commande = new Array[String](machines.size())
  for(i <- 0 to machines.size-1) {
     userMachine(i)= config.underlying.getString("machines."+i)
     commande(i)= Seq(chemin_ssh, userMachine(i), "last -x reboot && last -x shutdown").!!
     println(userMachine(i))
     println(commande(i))
  } 
  val c=Seq(chemin_ssh, "ndeyeanna@192.168.1.30", "last -x reboot && last -x shutdown").!!
  Ok(c)	
}
  
def etatCluster() = Action { 
  val chemin_ssh=config.underlying.getString("ssh")
  val user=config.underlying.getString("users.0") 
  val commande= Seq(chemin_ssh, user, "etcdctl cluster-health").!!
  println(commande)
  Ok(commande)	  
}
    
/****************************Gestion des Services******************************/
     
def creerService(nomservice:String) = Action.async (parse.json){ request =>
    val nomService=nomservice
    val listen_fleet=config.underlying.getString("fleet-listening")
    val chemin_ssh=config.underlying.getString("ssh")
    val user=config.underlying.getString("users.0") 
    
    val json=request.body  
    ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomService).put(json).map{ 
      val commande= Seq(chemin_ssh, user, "fleetctl cat "+nomService).!!
      response => Ok(commande) }
}
  
def startService(nomService:String) =	Action.async{
    val nomservice=nomService 
    val listen_fleet=config.underlying.getString("fleet-listening")
    val data = Json.obj("desiredState" -> "launched")
    ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomservice).put(data).map{response => Ok("Le service "+nomservice+" a bien demarre")}     
}
  
def statusService(nomService:String) = Action.async {
   val nomservice=nomService  
   val listen_fleet=config.underlying.getString("fleet-listening")
	 ws.url("http://192.168.1.30:49153/fleet/v1/state/"+nomservice).get().map{ response => Ok(response.body)}
}
  
def stopService(nomService:String) =	Action.async {
  val nomservice=nomService 
  val listen_fleet=config.underlying.getString("fleet-listening")
  val data = Json.obj("desiredState" -> "inactive")
  ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomservice).put(data).map{response =>  Ok("Le service "+nomservice+" s'est arrêté et n'est plus charge ") }
}
  
def listerServices() = Action.async { 
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url("http://192.168.1.30:49153/fleet/v1/units").get().map{ response => Ok(response.body)}
}
  
def loadService(nomService:String) =	Action.async{
  val nomservice=nomService 
  val listen_fleet=config.underlying.getString("fleet-listening")
  val data = Json.obj("desiredState" -> "loaded")
  ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomservice).put(data).map{response =>  Ok("Le service "+nomservice+" a bien ete charge") }   
}
   
def statusServices() = Action.async {
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url("http://192.168.1.30:49153/fleet/v1/state/").get().map{ response => Ok(response.body)}
}
   
def statusServicesMachine(nomService:String, machineId:String) = Action.async {
  val nomservice=nomService
  val machineID=machineId 
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url("http://192.168.1.30:49153/fleet/v1/state/"+nomservice+"/"+machineID).get().map{ response => Ok(response.body)}
}
   
def afficherService(nomService:String) = Action.async {
  val nomservice=nomService 
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomservice).get().map{ response => Ok(response.body)}
}
    
def deleteService(nomService:String) = Action.async {
  val nomservice=nomService  
  val listen_fleet=config.underlying.getString("fleet-listening")
  ws.url("http://192.168.1.30:49153/fleet/v1/units/"+nomservice).delete().map{ response => Ok("Le service "+nomservice+" a ete supprime")}
}
   
      
   
/*****************************Gestion des Conteneurs****************************/
    
def listerConteneurs() =	Action {      
  val chemin_ssh=config.underlying.getString("ssh")
  val machines=config.underlying.getObject("machines")
  val userMachine = new Array[String](machines.size())
  val commande = new Array[String](machines.size())
  for(i <- 1 to machines.size-1) {
      userMachine(i)= config.underlying.getString("machines."+i)
      commande(i)= Seq(chemin_ssh, userMachine(i), "docker images").!!
      println(userMachine(i))
      println(commande(i))
  } 
  Ok(Json.toJson(commande))
}
   
def adresseConteneur(idConteneur:String, hostname:String) = Action {    
  val chemin_ssh=config.underlying.getString("ssh")
  val host=hostname
  val containerId=idConteneur
  val commande= Seq(chemin_ssh, host, "docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "+containerId).!!
  Ok(Json.toJson(commande))
}
   
def configConteneur(idConteneur:String, hostname:String) = Action {      
  val chemin_ssh=config.underlying.getString("ssh")
  val host=hostname
  val containerId=idConteneur
  val commande= Seq(chemin_ssh, host, "docker inspect "+containerId).!!
  Ok(Json.toJson(commande))
}
  	    
def logsConteneur(idConteneur:String, hostname:String) = Action {      
  val chemin_ssh=config.underlying.getString("ssh")
  val host=hostname
  val containerId=idConteneur
  val commande= Seq(chemin_ssh, host, "docker logs "+containerId+" && docker top "+containerId).!!
  Ok(Json.toJson(commande))
}
       
/************************Gestion des Services Secondaires**************************/
  
def statusServiceSec(serviceSec: String) = Action { request =>  	 
  val chemin_ssh=config.underlying.getString("ssh")
  val user=config.underlying.getObject("users")
  val service=serviceSec   
  val userID = new Array[String](user.size())
  val commande = new Array[String](user.size())
  for(i <- 0 to user.size-1) {
     userID(i)= config.underlying.getString("users."+i)
     commande(i)= Seq(chemin_ssh, userID(i), "systemctl status -l "+service+" | grep active").!!
     println(userID(i))
     println(commande(i))
  } 
  Ok(Json.toJson(commande))	
}

def skydnsperforms() = Action {request =>     
  val chemin_ssh=config.underlying.getString("ssh")
  val user=config.underlying.getObject("users")   
  val userID = new Array[String](user.size())
  val commande = new Array[String](user.size())
  for(i <- 0 to user.size-1) {
     userID(i)= config.underlying.getString("users."+i)
     commande(i)= Seq(chemin_ssh, userID(i), "dig").!!
     println(userID(i))
     println(commande(i))
  } 
  val c = Seq(chemin_ssh, "ndeyeanna@192.168.1.30", "dig").!!
  Ok(c)
}
        
    
    
    
    
    
    
    
    
    
   
   /*def root(nomService:String)= Action(parse.json) { request =>
     val json = request.body 
     val desiredState=(json \ "desiredState").as[String]
     //println(desiredState)
     val op=(json \ "options").as[List[JsValue]]
     var b = new Array[String](op.size())
     var c = new Array[String](op.size())
     var d = new Array[String](op.size())
     // val d = op.map { p =>(json \ "section").asOpt[String]}
     for(i<-0 to op.size-1){
       b(i)=(op(i) \ "section").as[String]
       c(i)=(op(i) \ "name").as[String]
       d(i)=(op(i) \ "value").as[String]
       println(b(i))
       println(c(i))
       println(d(i))
     }
    //println(d)
    //val e = op.map { p =>(json \ "name").as[String]}
    // println(e)
    val f = op.map { p =>(json \ "value").asOpt[String]}
    //  println(f)
    val nana=Seq("echo", "nanandiaye").!!
    val v=nomService
    Ok(json)
  } */
    
  /* 
  *def delMachine(machineID : String) =Action {
  val chemin_ssh=config.underlying.getString("ssh")
  val user=config.underlying.getString("users.0") 
  val machineId=machineID
  val commande= Seq(chemin_ssh, user, "etcdctl member remove "+machineId).!!
  println(commande)
  Ok(Json.toJson(commande))	
}
        
def addMachine(name:String, advertisedPeerURLs:String) = Action {     
  val chemin_ssh=config.underlying.getString("ssh")
  val user=config.underlying.getString("users.0") 
  val nameMachine=name
  val ipPeer=advertisedPeerURLs
  val commande= Seq(chemin_ssh, user, "etcdctl member add "+nameMachine+" "+ipPeer).!!
  println(commande)
  Ok(Json.toJson(commande))	
}
  
  */
   /*def deleteService(nomService:String) =	Action {
      
     val chemin_ssh=config.underlying.getString("ssh")
     val user=config.underlying.getString("users.0") 
     val serviceName=nomService
     val commande= Seq(chemin_ssh, user, "fleetctl destroy "+serviceName).!!
     println(commande)
  	 Ok(Json.toJson(commande))	
  }*/
   
   
   /*def stopService(nomService:String) =	Action {
     
    val chemin_ssh=config.underlying.getString("ssh")
    val user=config.underlying.getString("users.0") 
    val serviceName=nomService
    val commande= Seq(chemin_ssh, user, "fleetctl stop "+serviceName).!!
    println(commande)
  	Ok(Json.toJson(commande))	
  }*/
    /*def startService(nomService:String) =	Action {
     
      val chemin_ssh=config.underlying.getString("ssh")
      val user=config.underlying.getString("users.0") 
      val serviceName=nomService
      val commande= Seq(chemin_ssh, user, "fleetctl start "+serviceName).!!
       println(commande)
  	 Ok(Json.toJson(commande))	
  }*/
  
 
  
  /* def listerServices() =	Action {
       val name = HostIP.load.get 
       // val n="192.168.1.17"
       // println("\n\n ***********************: Adress IP : " + name )
  	   Redirect("http://"+name+":49153/fleet/v1/state/")
    }*/

  
  /* def statusService(nomService:String) =	Action {
       val name = HostIP.load.get 
       val nomservice=nomService
       // val n="192.168.1.17"
       // println("\n\n ***********************: Adress IP : " + name )
  	   Redirect("http://"+name+":49153/fleet/v1/state/"+nomservice)
     }*/
   
   
  /* def listMachines() = Action {
       val name = HostIP.load.get 
       // val n="192.168.1.17"
       // println("\n\n ***********************: Adress IP : " + name )
  	   Redirect("http://"+name+":49153/fleet/v1/machines")
     }*/
   
   /*def listMachines() = Action(parse.json) { reauest =>
       val json = reauest.body
  	   Redirect("http://192.168.1.17:49153/fleet/v1/machines")
     }*/
    
   /*def fleetstatus(hostname: String)= Action { implicit request =>
       //    val commande= Seq("/usr/bin/ssh", REMOTE_HOST, "systemctl status -l fleet | grep active").!!
       //val fleetstatus = commande.map(f => JsString(f.toString))
        
       val chemin_ssh=config.underlying.getString("ssh") 
       val user=config.underlying.getObject("users")
       var userID = new Array[String](user.size())
       for(i <- 0 to user.size-1) {
         userID(i)= config.underlying.getString("users."+i)
         println(userID(i))
       } 
       val commande= Seq(chemin_ssh, hostname, "systemctl status -l fleet | grep active").!!    
       val json = Json.obj(
         "state" -> commande.toString
         )
       Ok(json)
   } 
  */
  
   /*def fleetstatus(hostname: String)= Action.async { request =>
        
       val chemin_ssh=config.underlying.getString("ssh") 
       val user=config.underlying.getObject("users")
       var result = Vector[String]()
       var userID = new Array[String](user.size())    
       for {
         n <- 0 to user.size-1
       } result = result :+ config.underlying.getString("users."+n)
       println(result.length)
       val commande= Seq(chemin_ssh, hostname, "systemctl status -l fleet | grep active").!!
       val json = Json.obj(
         "state" -> commande.toString
       )
       Ok(json)
   }*/
   
   /*def loadService(nomService:String) =	Action {
     
      val chemin_ssh=config.underlying.getString("ssh")
      val user=config.underlying.getString("users.0") 
      val serviceName=nomService
      val commande= Seq(chemin_ssh, user, "fleetctl load "+serviceName).!!
       println(commande)
  	 Ok(Json.toJson(commande))	
  }*/
  
   /*def replicasetStatus() = Action {
       val driver = new MongoDriver
       val con = driver.connection(List("localhost"))
       val db=con.db
  	   Ok(Json.toJson(result))
    }*/
} 
