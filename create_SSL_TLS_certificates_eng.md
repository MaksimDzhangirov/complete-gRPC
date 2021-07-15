# How to create & sign SSL/TLS certificates
In the [previous lecture](create_SSL_TLS_certificates_eng.md), we’ve talked 
about how digital certificates help with authentication and provide a safe and
reliable key exchange process in TLS. Today we will learn exactly how to 
generate a certificate and have it signed by a Certificate Authority (CA). For
the purpose of this demo, we won't submit our Certificate Signing Request (CSR)
to a real CA. Instead, we will play both roles: the certificate authority and 
the certificate applicant. So in the first step, we will generate a private 
key and its self-signed certificate for the CA. They will be used to sign the 
CSR later. In the second step, we will generate a private key and its paired 
CSR for the web server that we want to use TLS. Then finally we will use the 
CA's private key to sign the web server's CSR and get back the signed 
certificate.

```shell
# 1. Generate CA's private key and self-signed certificate

# 2. Generate web server's private key and certificate signing request (CSR)

# 3. Use CA's private key to sign web server's CSR and get back the signed certificate
```

In order to do all of these things, we need to have `openssl` installed. If 
you're on a mac, it's probably already there. You can run `openssl version` to
see which version it's running. In my case, it's LibreSSL version 2.8.3. Let's
open the browser and go to `libressl.org`. Here we have a [link](https://man.openbsd.org/openssl.1) 
to the manual of `openssl`.

## Generate CA's private key and certificate
The first command we're gonna used is `req`, which stands for `request`. This 
command is used to create and process certificate request. It can also be used 
to create a self-signed certificate for the CA, which is exactly what we want 
in the first step. This `-x509` option is used to tell `openssl` to output a 
self-signed certificate instead of a certificate request. In case you don't 
know, X509 is just a standard format of the public key certificate. You can 
click on a lock button of any HTTPS website to see its certificate in X509 
format. Alright, now let's get back to the terminal and run:

```shell
openssl req -x509 -newkey rsa:4096 -days 365 -keyout ca-key.pem -out ca-cert.pem
```

The `-newkey rsa:4096` option basically tells `openssl` to create both a new
private key with RSA 4096-bit key and its certificate at the same time. As 
we're using `-x509` option, it will output a certificate instead of a request. 
The next option is `-days 365`, which specifies the number of days that the 
certificate is valid for. Then we use `-keyout` option to tell `openssl` to 
write the created private key to `ca-key.pem` file and finally the `-out` 
option to tell it to write the certificate to `ca-cert.pem` file. When we 
press `Enter`, `openssl` will start generating the private key. Once the key is
generated, we will be asked to provide a pass phrase, which will be used to 
encrypt the private key before writing it to the PEM file. Why is it encrypted?
Because if somehow the private key file is hacked, the hacker cannot use it to 
do anything without knowing the pass phrase to decrypt it first. Next, 
`openssl` will ask us for some identity information to generate the 
certificate. First the country code, the state or province name, the city name,
the organization name, the unit name, the common name, or domain name, the 
email address. That's it! The certificate and private key files are 
successfully generated. If we `cat` the private key file, we can see it says 
"ENCRYPTED PRIVATE KEY":

`ca-key.pem`
```
-----BEGIN ENCRYPTED PRIVATE KEY-----
```

The certificate, on the other hand, is not encrypted, but only base64-encoded,
because it just contains the public key, the identity information and the 
signature that should be visible to everyone. We can use the `x509` command to
display all the information encoded in this certificate. This command can also
be used to sign certificate requests, which we will do in a few minute. Now
let's run

```shell
openssl x509 -in ca-cert.pem -noout -text
```

Here we use the `-in` option to pass in the CA’s certificate file, the 
`-noout` option to tell it to not output the original encoded value. We want 
to display it in a readable text format, so let's use `-text` option and press
`Enter`. Now we can see all information of the certificate, such as the 
version, the serial number. The issuer and the subject are the same in this 
case because this is a self-signed certificate. Then the RSA public key and
signature. I'm gonna copy this command and save it to our `gen.sh` script. With
this script, I want to automate te process of generating a set of keys and
certificates.

```shell
# 1. Generate CA's private key and self-signed certificate
openssl req -x509 -newkey rsa:4096 -days 365 -keyout ca-key.pem -out ca-cert.pem

echo "CA's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text
```

Before moving to the 2nd step, I’m gonna show you another way to provide the 
identity information without entering it interactively as before. To do this,
we use the `subj` option, I'm gonna add it to `openssl` request command
and copy identity information from the certificate.

```shell
openssl req -x509 -newkey rsa:4096 -days 365 -keyout ca-key.pem -out ca-cert.pem -subj "/C=FR/ST=Occitanie/L=Toulouse/O=Tech School/OU=Education/CN=*.techschool.guru/emailAddress=techschool.guru@gmail.com"
```

Now let's add a command to remove all `pem` files at the top of this script,
then run `gen.sh` in the terminal. We still being prompted for a pass phrase,
but it doesn't ask for identity information anymore, because we already 
provided them in the `subj` option. Great!

## Generate web server's private key and CSR
Now the next step is to generate a private key and CSR for out web server. It's
almost the same as the command we used in the 1st step. Except that, this time
we don't want to self-sign it, so we should remove this `-x509` option. This
`-days` option should be removed as well, since we don't create a certificate,
but just a CSR. Then we change the name of the output key to `server-key.pem`.
The output certificate request file should be `server-req.pem`, because we're
creating a certificate signing request. Now we should change all the subject 
information to our web server's information. OK, let's run it. 

```shell
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@gmail.com"
```

Enter a pass phrase to encrypt the web server's private key. Then the files are
successfully generated.

This time, in the `server-req.pem` file, it says `CERTIFICATE REQUEST`, not 
`CERTIFICATE` as in the `ca-cert.pem` file. That's because it’s not a 
certificate as before, but a certificate signing request instead.

```
-----BEGIN CERTIFICATE REQUEST-----
MIIE2DCCAsACAQAwgZIxCzAJBgNVBAYTAkZSMRYwFAYDVQQIDA1JbGUgZGUgRnJh
bmNlMQ4wDAYDVQQHDAVQ...pWofr2eOeBQ4Q=
-----END CERTIFICATE REQUEST-----
```

```shell
# ...

# 2. Generate web server's private key and certificate signing request (CSR)
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@gmail.com"

# ...
```

So now let's move to step 3 and sign this request.

## Sign the web server's certificate request
For that, we will use the same `x509` command that we've used to display 
certificate before. Let's open the terminal and run this:

```shell
openssl x509 -req -in server-req.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem
```

In this command, we use the `-req` option to tell openssl that we’re gonna pass
in a certificate request. We use the `-in` option follow by the name of the 
request file: `server-req.pem`. Next we use the `-CA` option to pass in the 
certificate file of the CA: `ca-cert.pem`. And the `-CAkey` option to pass in
the private key of the CA: `ca-key.pem`. Then 1 important option is 
`-CAcreateserial`. Basically the CA must ensure that each certificate it signs 
goes with a unique serial number. So with this option, a file containing the 
next serial number will be generated if it doesn't exist. Finally we use the
`-out` option to specify the file to write the output certificate to. Now as
you can see here, because the CA's private key is encrypted, `OpenSSL` is 
asking for the pass phrase to decrypt it before it can be used to sign the 
certificate. It’s a countermeasure in case the CA’s private key is hacked. OK,
now we've got the signed certificate for our web server. Let's print it out 
in text format.

```shell
openssl x509 -in server-cert.pem -noout -text
```

It has its unique serial number `0xb141e873fd7b8567`. We can also see a 
`ca-cert.srl` file, which contains the same serial number.

```shell
B141E873FD7B8567
```

Issuer section contains the information of the CA, which is Tech School in this
case. By default, the certificate is valid for 30 days. We can change it by 
adding the `-days` option to the signing command.

```shell
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem
```

Now the validity duration has changed to 60 days.

If you remember the Youtube certificate that we've seen in the previous lecture, 
this certificate is used for many Google websites with different domain names. 
We can also do that for our web server by specifying the Subject Alternative 
Name extension when signing the certificate request. Here we can see the 
`-extfile` option that allows us to state the file containing the extensions. 
We can see the format of this config file in [this page](https://man.openbsd.org/x509v3.cnf.5#Subject_alternative_name).
Let's search for "subject alternative name".

There are several things that we can use as the alternative name, such as 
`email`, `DNS`, or `IP`. I will create a new file `server-ext.cnf` with this
content:

```shell
subjectAltName=DNS:*.pcbook.com,DNS:*.pcbook.org,IP:0.0.0.0
```

Here I set DNS to multiple domain names: `*.pcbook.com` and `*.pcbook.org`. I 
also set IP to `0.0.0.0` which will be used when we develop on localhost. Now
in the certificate signing command, let's add the `-extfile` option and pass
in the name of the extension config file:

```shell
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf
```

Now the result certificate file has a new extensions section with all the 
subject alternative names that we've chosen:

```shell
Certificate:
    ...
    Signature Algorithm: sha1WithRSAEncryption
        Issuer: C=FR, ST=Occitanie, L=Toulouse, O=Tech School, OU=Education, CN=*.techschool.guru/emailAddress=techschool.guru@gmail.com
        Validity
            Not Before: Apr 10 18:17:05 2020 GMT
            Not After : Jun  9 18:17:05 2020 GMT
        Subject: C=FR, ST=Ile de France, L=Paris, O=PC Book, OU=Computer, CN=*.pcbook.com/emailAddress=pcbook@gmail.com
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (4096 bit)
                Modulus:
                    00:cb:e2:2b:c3:68:...
                Exponent: 65537 (0x10001)
        X509v3 extensions:
            X509v3 Subject Alternative Name: 
                DNS:*.pcbook.com, DNS:*.pcbook.org, IP Address:0.0.0.0
    Signature Algorithm: sha1WithRSAEncryption
         5e:67:4d:f7:91:89:fc:...
```

So looks like our automate script is ready except for the fact that we have to 
enter a lot of password to protect the private keys.

```shell
# ...

# 3. Use CA's private key to sign web server's CSR and get back the signed certificate
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

echo "Server's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text
```

In case we just want to use this for development and testing, we can tell
`openssl` to not encrypt the private key, so that it won't ask us for the pass 
phrase.

We do that by adding the `-nodes` option to the `openssl req` command like 
this:

```shell
# 1. Generate CA's private key and self-signed certificate
openssl req -x509 -newkey rsa:4096 -days 365 -nodes -keyout ca-key.pem -out ca-cert.pem -subj "/C=FR/ST=Occitanie/L=Toulouse/O=Tech School/OU=Education/CN=*.techschool.guru/emailAddress=techschool.guru@gmail.com"

echo "CA's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text

# 2. Generate web server's private key and certificate signing request (CSR)
openssl req -newkey rsa:4096 -nodes -keyout server-key.pem -out server-req.pem -subj "/C=FR/ST=Ile de France/L=Paris/O=PC Book/OU=Computer/CN=*.pcbook.com/emailAddress=pcbook@gmail.com"

# 3. Use CA's private key to sign web server's CSR and get back the signed certificate
openssl x509 -req -in server-req.pem -days 60 -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

echo "Server's self-signed certificate"
openssl x509 -in ca-cert.pem -noout -text
```

Now if I run `gen.sh` again, it will not ask for passwords anymore. And if we 
look at the private key files, it will be `PRIVATE KEY`, and not 
`ENCRYPTED PRIVATE KEY` as before.

```shell
-----BEGIN PRIVATE KEY-----
MIIJQwIBADANBgkqhkiG9w0BAQEFAASCCS0wggkpAgEAAoICAQDL4ivDaIzDM3my
VDzT2Mw5R9bicXS...AxAt2Ldmc4=
-----END PRIVATE KEY-----
```

## Verify a certificate
One last thing before we finish, I will show you how to verify if a certificate
is valid or not. We can do that with the `openssl verify` command:

```shell
openssl verify -CAfile ca-cert.pem server-cert.pem
```

We just pass in the trusted CA's certificate and the certificate that we want
to verify. If it returns OK then the certificate is valid.

And that's it for today's lecture. I hope it's useful for you. Thanks for 
reading, and I’ll see you guys in the next one!