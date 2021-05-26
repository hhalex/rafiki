package com.lion.rafiki.auth

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import cats.effect.testing.specs2.CatsEffect
import cats.effect.IO
import cats.data.EitherT
import com.lion.rafiki.domain.{User, RepoError, Company}
import org.specs2.specification.Scope
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.Authorization
import org.http4s.Header
import java.util.Base64
import org.http4s.Request
import org.http4s.Credentials
import org.http4s.AuthScheme

class UserAuthSpec extends Specification with CatsEffect with Mockito {
    val passwordHasher = new PasswordHasher[IO] {
        override def hashPwd(pwd: String) = EitherT.rightT(PasswordHasher.tag(pwd))
        override def checkPwd(clear: String, pwd: PasswordHasher.Password) = EitherT.rightT(clear == pwd)
    }
    val hotCreds = UserCredentials("admin", "pass")
    val companyCreds = UserCredentials("company", "pwd")
    val employeeCreds = UserCredentials("employee", "employeePwd")
    val hotUsers = Seq(hotCreds, UserCredentials("admin2", "pass2"))
    val hotUserStore = new HotUserStore(hotUsers, passwordHasher)

    "HotUserStore" >> {
        "isMember" >> {
            "finds hot user in store" >>
                hotUserStore.isMember(User.Authed(hotCreds.username)).value
                    .map(_ must beSome(User.Authed(hotCreds.username)))
            "fails if hot user not in store" >>
                hotUserStore.isMember(User.Authed("notInStore")).value
                    .map (_ must beNone)
        }
        "validatesCredentials" >> {
            "validates correct credentials" >>
                hotUserStore.validateCredentials(hotCreds).value
                    .map(_ must beRight(User.Authed(hotCreds.username)))
            "errors when user not found" >>
                hotUserStore.validateCredentials(hotCreds.copy(username = "ad")).value
                    .map(_ must beLeft(AuthError.UserNotFound))
            "errors when password is wrong" >>
                hotUserStore.validateCredentials(hotCreds.copy(password = "wrong")).value
                    .map(_ must beLeft(AuthError.InvalidPassword))

        }
    }

    def createUserAuth(m: (u: User.Repo[IO], c: Company.Repo[IO]) => Any = (_, _) => {}): UserAuth[IO] = {
        val userRepo = mock[User.Repo[IO]]
        val companyRepo = mock[Company.Repo[IO]]
        m(userRepo, companyRepo)
        val privateKey = PrivateKey("test".getBytes)
        val cryptoBits = CryptoBits(privateKey)
        new UserAuth(userRepo, companyRepo, hotUserStore, cryptoBits, passwordHasher)
    }

    "UserAuth" >> {
        "validatesCredentials" >> {
            "validates creds among hot users" >> {
                val userAuth = createUserAuth()
                userAuth.validateCredentials(hotCreds).value.map { _ must beRight(User.Authed(hotCreds.username)) }
            }
            "fails for invalid password in hot users" >> {
                val userAuth = createUserAuth()
                userAuth.validateCredentials(hotCreds.copy(password = "wrong")).value.map { _ must beLeft(AuthError.InvalidPassword) }
            }
            "validates creds among classic users" >> {
                val userAuth = createUserAuth { (userRepo, _) =>
                    userRepo.findByUserName(any()) returns EitherT.rightT[IO, RepoError](
                        User(None, None, employeeCreds.username, PasswordHasher.tag(employeeCreds.password)).withId(User.tag(3))
                    )
                }
                userAuth.validateCredentials(employeeCreds).value map { _ must beRight(User.Authed(employeeCreds.username)) }
            }
            "fails for invalid password in classic users" >> {
                val userAuth = createUserAuth { (userRepo, _) =>
                    userRepo.findByUserName(any()) returns EitherT.leftT[IO, User.Authed](AuthError.InvalidPassword)
                }
                userAuth.validateCredentials(employeeCreds).value map { _ must beLeft(AuthError.InvalidPassword) }
            }
        }
        "embedAuthHeader" >> {
            "check Authorization header is present" >> {
                import org.typelevel.ci.CIStringSyntax
                val userAuth = createUserAuth()
                val taggedResponse = userAuth.embedAuthHeader(Response(Status.Ok), User.Authed("test"), "01234")
                taggedResponse.headers.headers.map(_.name) must contain(ci"Authorization")
            }
            "check Authorization header is base64 encoded" >> {
                import org.typelevel.ci.CIStringSyntax
                val userAuth = createUserAuth()
                val taggedResponse = userAuth.embedAuthHeader(Response(Status.Ok), User.Authed("test"), "01234")
                val authHeader = taggedResponse.headers.headers.find(_.name == ci"Authorization")
                val base64decoder = Base64.getDecoder
                (authHeader.get.value must contain("Bearer")) and {
                    val b64 = authHeader.get.value.substring("Bearer ".length)
                    new String(base64decoder.decode(b64)) must beMatching("""^[^-]+\-01234\-test$""")
                }
            }
        }
        "auth" >> {
            "fail when no Authorization header" >> {
                val userAuth = createUserAuth()
                userAuth.auth(Request()).value map { _ must beLeft(AuthError.AuthorizationTokenNotFound) }
            }
            "fail when no Authorization header is invalid" >> {
                val userAuth = createUserAuth()
                userAuth.auth(Request().withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "invalid")))).value
                    .map { _ must beLeft(AuthError.InvalidToken) }
            }
            "succeed when Authorization header is valid" >> {
                val userAuth = createUserAuth()
                userAuth.auth(Request().withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "ZDRmZWI4ZTljNWQ4NWU3MDMxNDlhYjI3ZDRhYTllYTMwNTFlZGQ1Yi0wMTIzNC10ZXN0")))).value
                    .map { _ must beRight(User.Authed("test")) }
            }
        }
        "role" >> {
            "determines Admin correctly" >> {
                val userAuth = createUserAuth()
                userAuth.role(User.Authed("admin")).value.map { _ must beRight("Admin") }
            }
            "determines Company correctly" >> {
                val userAuth = createUserAuth({
                    (uRepo, cRepo) => {
                        uRepo.findByUserName("company") returns EitherT.leftT[IO, User.Record](RepoError.NotFound)
                        cRepo.getByUserEmail("company") returns EitherT.rightT[IO, RepoError](Company("name", User(None, None, "user", PasswordHasher.tag("pass"))))
                    }
                })
                userAuth.role(User.Authed("company")).value.map { _ must beRight("Company") }
            }
            "determines Employee correctly" >> {
                val userAuth = createUserAuth({
                    (uRepo, cRepo) => {
                        uRepo.findByUserName(any()) returns EitherT.rightT[IO, RepoError](User(None, None, "employee", PasswordHasher.tag("pass")).withId(User.tag(2)))
                        cRepo.getByUserEmail(any()) returns EitherT.leftT[IO, Company.Record](RepoError.NotFound)
                    }
                })
                userAuth.role(User.Authed("employee")).value.map {
                    _ must beRight("Employee")
                }
            }
        }
    }
}