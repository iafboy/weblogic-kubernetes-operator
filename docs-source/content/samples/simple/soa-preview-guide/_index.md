---
title: "SOA Preview Guide"
date: 2019-10-14T11:21:31-05:00
weight: 7
description: "End-to-end guide for SOA Suite preview testers."
---

### End-to-end guide for Oracle SOA Suite preview testers

This document provides detailed instructions for testing the Oracle SOA Suite preview. 
This guide uses the WebLogic Kubernetes operator version 2.3.0 and SOA Suite 12.2.1.3.0.
SOA Suite has also been tested using the WebLogic Kubernetes operator version 2.2.1.
SOA Suite is currently a *preview*, meaning that everything is tested and should work,
but official support is not available yet. 
You can, however, come to [our public Slack](https://weblogic-slack-inviter.herokuapp.com/) to ask questions
and provide feedback.
At Oracle OpenWorld 2019, we did announce our *intention* to provide official
support for SOA Suite running on Kubernetes in 2020 (subject to the standard Safe Harbor statement).
For planning purposes, it would be reasonable to assume that the production support would
likely be for Oracle SOA Suite 12.2.1.4.0.

{{% notice warning %}}
Oracle SOA Suite is currently only supported for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}

#### Overview

This guide will help you to test the Oracle SOA Suite preview in Kubernetes.  The guide presents
a complete end-to-end example of setting up and using SOA Suite in Kubernetes including:

* [Preparing your Kubernetes cluster](#preparing-your-kubernetes-cluster).
* [Obtaining the necessary Docker images](#obtaining-the-necessary-docker-images).
* [Installing the WebLogic Kubernetes operator](#installing-the-weblogic-kubernetes-operator).
* [Preparing your database for the SOAINFRA schemas](#preparing-your-database-for-the-soainfra-schemas).
* [Running the Repository Creation Utility to populate the database](#running-the-repository-creation-utility-to-populate-the-database).
* [Creating a SOA domain](#creating-a-soa-domain).
* [Starting the SOA domain in Kubernetes](#starting-the-soa-domain-in-kubernetes).
* [Setting up a load balancer to access various SOA endpoints](#setting-up-a-load-balancer-to-access-various-soa-endpoints).
* [Configuring the SOA cluster for access through a load balancer](#configuring-the-soa-cluster-for-access-through-a-load-balancer).
* [Deploying a SCA composite to the domain](#deploying-a-sca-composite-to-the-domain).
* [Accessing the SCA composite and various SOA web interfaces](#accessing-the-sca-composite-and-various-soa-web-interfaces). 
* [Configuring the domain to send logs to Elasticsearch](#configuring-the-domain-to-send-logs-to-elasticsearch).
* [Using Kibana to view logs for the domain](#using-kibana-to-view-logs-for-the-domain).
* [Configuring the domain to send metrics to Prometheus](#configuring-the-domain-to-send-metrics-to-prometheus).
* [Using the Grafana dashboards to view metrics for the domain](#using-the-grafana-dashboards-to-view-metrics-for-the-domain).

{{% notice note %}}
**Feedback**  
If you find any issues with this guide, please [open an issue in our GitHub repository](https://github.com/oracle/weblogic-kubernetes-operator/issues/new)
or report it on [our public Slack](https://weblogic-slack-inviter.herokuapp.com/).  Thanks!
{{% /notice %}}

#### Preparing your Kubernetes cluster

To follow the instructions in this guide, you will need a Kubernetes cluster.
In this guide, the examples are shown using Oracle Container Engine for Kubernetes,
Oracle's managed Kubernetes service.  Detailed information can be found
[in the documentation](https://docs.cloud.oracle.com/iaas/Content/ContEng/Concepts/contengoverview.htm).
If you do not have your own Kuberentes cluster, you can [try Oracle Cloud for free](https://www.oracle.com/cloud/free/)
and get a cluster using the free credits, which will provide enough time to work through this 
whole guide. You can also use any of the other [supported Kubernetes distributions]({{< relref "/userguide/introduction/introduction" >}}).

##### A current version of Kubernetes 

To confirm that your Kubernetes cluster is suitable for SOA Suite, you should confirm
you have a resonably recent version of Kubernetes, 1.13 or later is recommended.
You can check the version of Kubernetes with this command:

```bash
$ kubectl version
Client Version: version.Info{Major:"1", Minor:"15", GitVersion:"v1.15.3", GitCommit:"2d3c76f9091b6bec110a5e63777c332469e0cba2", GitTreeState:"clean", BuildDate:"2019-08-19T11:13:54Z", GoVersion:"go1.12.9", Compiler:"gc", Platform:"linux/amd64"}
Server Version: version.Info{Major:"1", Minor:"13+", GitVersion:"v1.13.5-6+d6ea2e3ed7815b", GitCommit:"d6ea2e3ed7815b9b53d854038041f43b0a98555e", GitTreeState:"clean", BuildDate:"2019-09-19T23:10:35Z", GoVersion:"go1.11.5", Compiler:"gc", Platform:"linux/amd64"}
```
This output shows that the Kubernetes cluster (the "Server Version" section) is running version 1.13.5.

##### Adequate CPU and RAM 

Make sure that your worker nodes have enough memory and CPU resource.  If you plan to run a SOA
domain with two managed servers and an admin server, plus a database, then a good
rule of thumb would be to have at least 12GB of available RAM between your worker nodes.
We came up with number by allowing 4GB each for the database, and each of the three 
WebLogic servers.

You can use the following commands to check how many worker nodes you have, and to check
the avilable CPU and memory for each:

```bash
$ kubectl get nodes
NAME        STATUS   ROLES   AGE   VERSION
10.0.10.2   Ready    node    54m   v1.13.5
10.0.10.3   Ready    node    54m   v1.13.5
10.0.10.4   Ready    node    54m   v1.13.5

$ kubectl get nodes -o jsonpath='{.items[*].status.capacity}' 
map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110] map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110] map[cpu:16 ephemeral-storage:40223552Ki hugepages-1Gi:0 hugepages-2Mi:0 memory:123485928Ki pods:110]
2019-10-30 09:39:21:~ 
```
From the output shown, you can see that this cluster has three worker nodes, and each one has 16 cores and about 120GB of RAM.

##### Helm installed

You will need to have Helm installed on your client machine (the machine where you run `kubectl` commands) and the "Tiller"
component installed in your cluster.

You can obtain Helm from their [releases page](https://github.com/helm/helm/releases/tag/v2.14.3). 
The examples in this guide use version 2.14.3.  You must ensure that the version you choose is
compatible with the version of Kubernetes that you are running. 

To install the "Tiller" component on your Kubernetes cluster, use this command:

```bash
$ helm init
```

It will take about 30-60 seconds for Tiller to be deployed and to start. 
To confirm that Tiller is running, use this command: 

```bash
$ kubectl -n kube-system get pods  | grep tiller
tiller-deploy-5545b55857-rq8gp          1/1     Running   0          81m
```
The output should show the status "Running".


{{% notice note %}}
All Kubernetes distributions and managed services have small differences.  In particular,
the way that persistent storage and load balancers are managed varies significantly.  
You may need to adjust the instructions in this guide to suit your particular flavor of Kubernetes.
{{% /notice %}}


#### Obtaining the necessary Docker images


#### Installing the WebLogic Kubernetes operator


#### Preparing your database for the SOAINFRA schemas


#### Running the Repository Creation Utility to populate the database


#### Creating a SOA domain


#### Starting the SOA domain in Kubernetes


#### Setting up a load balancer to access various SOA endpoints


#### Configuring the SOA cluster for access through a load balancer


#### Deploying a SCA composite to the domain


#### Accessing the SCA composite and various SOA web interfaces


#### Configuring the domain to send logs to Elasticsearch


#### Using Kibana to view logs for the domain


#### Configuring the domain to send metrics to Prometheus


#### Using the Grafana dashboards to view metrics for the domain

