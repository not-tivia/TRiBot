              /*
                   Deposit all if we are at the bank
                     */
                if (Banking.isInBank() || AREA_GE.contains(Player.getPosition())) {
                    if (Banking.isBankScreenOpen()) {
                        /*
                        We need to make space for everything we will potentially need
                        which includes
                        A spade, 6 armour pieces, coins,
                        and if collecting the helm - Nature rune,Leather boots,Superantipoison(1)
                         */
                        Banking.depositAll();
                        Timing.waitCondition(() -> openInventorySpots()==28, General.random(3000, 6000));

                        if (!hasItem("Spade")){
                            if (bankHasItem("Spade")){
                                if (Banking.withdraw(1,"Spade")){
                                    Timing.waitCondition(() -> hasItem("Spade"), General.random(3000, 6000));
                                }
                            } else {
                                needsSpade = true;
                            }
                        }

                        if (!hasItem("Coins") && bankHasItem("Coins")){
                            if (Banking.withdraw(General.random(5000,10000),"Coins")){
                                Timing.waitCondition(() -> hasItem("Coins"), General.random(3000, 6000));

                            }
                        }

                        if (tasks.contains("helm")){
                            /*
                            If we do not have the item in our inventory
                            Then check if we have the item in the bank
                            If we have the item in the bank, then withdraw,
                            If we do not have any of the items,
                            them remove helm from our tasks list
                             */
                            if (!hasItem("Nature rune")){
                                if (bankHasItem("Nature rune")){
                                    if (Banking.withdraw(1,"Nature rune")){
                                        Timing.waitCondition(() -> hasItem("Nature rune"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                            if (!hasItem("Superantipoison(1)")){
                                if (bankHasItem("Superantipoison(1)")){
                                    if (Banking.withdraw(1,"Superantipoison(1)")){
                                        Timing.waitCondition(() -> hasItem("Superantipoison(1)"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                            if (!hasItem("Leather boots")){
                                if (bankHasItem("Leather boots")){
                                    if (Banking.withdraw(1,"Leather boots")){
                                        Timing.waitCondition(() -> hasItem("Leather boots"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                        }

                        hasCheckedBank = true;
                    } else {
                        /*
                        Closes the new bank interface if needed
                        else open bank
                         */
                        RSInterface bankTutorialInterface = Interfaces.get(664, 28);
                        if (bankTutorialInterface != null) {
                            bankTutorialInterface.click();
                            Timing.waitCondition(() -> bankTutorialInterface == null, General.random(3000, 6000));
                        }
                        if (Banking.openBank()) {
                            Timing.waitCondition(() -> Banking.isBankScreenOpen(), General.random(2500, 4100));
                        }

                    }
                } else {
                    General.println("Walking to the nearest bank");
                    if (DaxWalker.walkToBank()) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                }
