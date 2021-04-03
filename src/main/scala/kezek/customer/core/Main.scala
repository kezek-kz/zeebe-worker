package kezek.customer.core

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.{Config, ConfigFactory}
import io.zeebe.client.ZeebeClient
import io.zeebe.client.impl.oauth.{OAuthCredentialsProvider, OAuthCredentialsProviderBuilder}
import kezek.customer.core.service.worker.{HttpJobWorker, InitJobWorker, SetVariablesJobWorker}

import scala.concurrent.ExecutionContext

object Main extends App {

  implicit val config: Config = ConfigFactory.load()

  implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.empty,
    name = config.getString("akka.actor.system"),
    config
  )

  implicit val zeebeCreds: OAuthCredentialsProvider = new OAuthCredentialsProviderBuilder()
    .audience(config.getString("zeebe.broker"))
    .authorizationServerUrl(config.getString("zeebe.authApi"))
    .clientId(config.getString("zeebe.clientId"))
    .clientSecret(config.getString("zeebe.clientSecret"))
    .build()

  implicit val zeebeClient: ZeebeClient = ZeebeClient.newClientBuilder()
    .gatewayAddress(config.getString("zeebe.broker"))
    .credentialsProvider(zeebeCreds)
    .build()

  implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
  implicit val executionContext: ExecutionContext = classicSystem.dispatchers.lookup("akka.dispatchers.main")

  zeebeClient.newWorker().jobType("http-request").handler(new HttpJobWorker())
  zeebeClient.newWorker().jobType("init").handler(new InitJobWorker())
  zeebeClient.newWorker().jobType("set-variables").handler(new SetVariablesJobWorker())
}
