/*
 * Copyright 2011-2018 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.compiler.config.cli

private[cli] case class CommandLineConstant(full: String, abbr: String)

private[cli] object CommandLineConstants {
  val SimulationsFolder = CommandLineConstant("simulations-folder", "sf")
  val BinariesFolder = CommandLineConstant("binaries-folder", "bf")
  val ExtraScalacOptions = CommandLineConstant("extra-scalac-options", "eso")
}