// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

@SuppressWarnings("unused")
class ReferencingObject {
  private SimpleObject simple;
  private DerivedObject derived;

  @Deprecated private int deprecatedField;
}
