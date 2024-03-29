package com.alphawallet.app.entity.tokens;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.alphawallet.app.entity.ContractType;
import com.alphawallet.app.entity.ERC875ContractTransaction;
import com.alphawallet.app.entity.TicketRangeElement;
import com.alphawallet.app.entity.Transaction;
import com.alphawallet.app.entity.TransactionOperation;
import com.alphawallet.app.repository.entity.RealmToken;
import com.alphawallet.app.service.AssetDefinitionService;
import com.alphawallet.app.ui.BaseActivity;
import com.alphawallet.app.ui.widget.holder.TokenHolder;
import com.alphawallet.app.viewmodel.BaseViewModel;
import com.alphawallet.app.web3.Web3TokenView;

import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import com.alphawallet.token.entity.TicketRange;
import com.alphawallet.token.entity.TokenScriptResult;
import com.alphawallet.token.tools.TokenDefinition;
import com.alphawallet.app.R;

/**
 * Created by James on 27/01/2018.  It might seem counter intuitive
 * but here Ticket refers to a container of an asset class here, not
 * the right to seat somewhere in the venue. Therefore, there
 * shouldn't be List<Ticket> To understand this, imagine that one says
 * "I have two cryptocurrencies: Ether and Bitcoin, each amounts to a
 * hundred", and he pauses and said, "I also have two indices: FIFA
 * and Formuler-one, which, too, amounts to a hundred each".
 */

public class Ticket extends Token implements Parcelable
{
    private final List<BigInteger> balanceArray;
    private boolean isMatchedInXML = false;

    public Ticket(TokenInfo tokenInfo, List<BigInteger> balances, long blancaTime, String networkName, ContractType type) {
        super(tokenInfo, BigDecimal.ZERO, blancaTime, networkName, type);
        this.balanceArray = balances;
        balanceUpdateWeight = calculateBalanceUpdateWeight();
    }

    public Ticket(TokenInfo tokenInfo, String balances, long blancaTime, String networkName, ContractType type) {
        super(tokenInfo, BigDecimal.ZERO, blancaTime, networkName, type);
        this.balanceArray = stringHexToBigIntegerList(balances);
        balanceUpdateWeight = calculateBalanceUpdateWeight();
    }

    private Ticket(Parcel in) {
        super(in);
        balanceArray = new ArrayList<>();
        int objSize = in.readInt();
        int interfaceOrdinal = in.readInt();
        contractType = ContractType.values()[interfaceOrdinal];
        if (objSize > 0)
        {
            Object[] readObjArray = in.readArray(Object.class.getClassLoader());
            for (Object o : readObjArray)
            {
                BigInteger val = (BigInteger)o;
                balanceArray.add(val);
            }
        }
    }

    @Override
    public String getStringBalance() {
        return bigIntListToString(balanceArray, false);
    }

    @Override
    public boolean hasPositiveBalance() {
        return (getTicketCount() > 0);
    }

    @Override
    public String getFullBalance() {
        if (balanceArray == null) return "no tokens";
        else return bigIntListToString(balanceArray, true);
    }

    public static final Creator<Ticket> CREATOR = new Creator<Ticket>() {
        @Override
        public Ticket createFromParcel(Parcel in) {
            return new Ticket(in);
        }

        @Override
        public Ticket[] newArray(int size) {
            return new Ticket[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(balanceArray.size());
        dest.writeInt(contractType.ordinal());
        if (balanceArray.size() > 0) dest.writeArray(balanceArray.toArray());
    }

    /**
     * Given a string of hex ticket ID's, reduce the length of the string to 'quantity' items
     *
     * @return
     */
    public List<BigInteger> pruneIDList(String idListStr, int quantity)
    {
        //convert to list
        List<BigInteger> idList = stringHexToBigIntegerList(idListStr);
        /* weiwu: potentially we can do this but I am not sure if
         * order is important*/
        //List<BigInteger> idList = Observable.fromArray(idListStr.split(","))
        //       .map(s -> Numeric.toBigInt(s)).toList().blockingGet();
        if (quantity >= idList.size()) return idList;
        List<BigInteger> pruneList = new ArrayList<>();
        for (int i = 0; i < quantity; i++) pruneList.add(idList.get(i));

        return pruneList;
    }

    @Override
    public int getTicketCount()
    {
        int count = 0;
        if (balanceArray != null)
        {
            for (BigInteger id : balanceArray)
            {
                if (id.compareTo(BigInteger.ZERO) != 0) count++;
            }
        }
        return count;
    }

    @Override
    public void setRealmBalance(RealmToken realmToken)
    {
        realmToken.setBalance(bigIntListToString(balanceArray, true));
    }

    @Override
    public void clickReact(BaseViewModel viewModel, Context context)
    {
        viewModel.showRedeemToken(context, this);
    }

    @Override
    public void setupContent(TokenHolder tokenHolder, AssetDefinitionService asset)
    {
        tokenHolder.balanceCurrency.setText("--");
        tokenHolder.textAppreciation.setText("--");

        tokenHolder.contractType.setVisibility(View.VISIBLE);
        tokenHolder.contractSeparator.setVisibility(View.VISIBLE);
        tokenHolder.layoutValueDetails.setVisibility(View.GONE);
        if (contractType == ContractType.ERC875_LEGACY)
        {
            tokenHolder.contractType.setText(R.string.erc875legacy);
        }
        else
        {
            tokenHolder.contractType.setText(R.string.erc875);
        }

        String composite = getTicketCount() + " " + getFullName(asset, getTicketCount());

        tokenHolder.balanceEth.setText(composite);
    }

    @Override
    public int[] getTicketIndices(String ticketIds)
    {
        List<BigInteger> indexList = ticketIdStringToIndexList(ticketIds);
        int[] indicies = new int[indexList.size()];
        int i = 0;
        for (Iterator<BigInteger> iterator = indexList.iterator(); iterator.hasNext(); i++) {
            indicies[i] = iterator.next().intValue();
        }
        return indicies;
    }

    /*************************************
     *
     * Conversion functions used for manipulating indices
     *
     */


    /**
     * Convert a list of TicketID's into an Index list corresponding to those indices
     * @param ticketIds
     * @return
     */
    public List<BigInteger> ticketIdListToIndexList(List<BigInteger> ticketIds)
    {
        //read given indicies and convert into internal format, error checking to ensure
        List<BigInteger> idList = new ArrayList<>();

        try
        {
            for (BigInteger id : ticketIds)
            {
                if (id.compareTo(BigInteger.ZERO) != 0)
                {
                    int index = balanceArray.indexOf(id);
                    if (index > -1)
                    {
                        if (!idList.contains(BigInteger.valueOf(index))) //just make sure they didn't already add this one
                        {
                            idList.add(BigInteger.valueOf(index));
                        }
                    }
                    else
                    {
                        idList = null;
                        break;
                    }
                }
            }
        }
        catch (Exception e)
        {
            idList = null;
        }

        return idList;
    }

    /**
     * Convert a String list of ticket IDs into a list of ticket indices
     * @param userList
     * @return
     */
    @Override
    public List<BigInteger> ticketIdStringToIndexList(String userList)
    {
        List<BigInteger> idList = new ArrayList<>();

        String[] ids = userList.split(",");

        for (String id : ids)
        {
            //remove whitespace
            String     trim   = id.trim();
            BigInteger thisId = Numeric.toBigInt(trim);
            idList.add(thisId);
        }

        return tokenIdsToTokenIndices(idList);
    }

    private List<BigInteger> tokenIdsToTokenIndices(List<BigInteger> tokenIds)
    {
        List<BigInteger> inventoryCopy = new ArrayList<BigInteger>(balanceArray);
        List<BigInteger> indexList = new ArrayList<>();
        try
        {
            for (BigInteger id : tokenIds)
            {
                if (id.compareTo(BigInteger.ZERO) != 0)
                {
                    int index = inventoryCopy.indexOf(id);
                    if (index > -1)
                    {
                        inventoryCopy.set(index, BigInteger.ZERO);
                        BigInteger indexBi = BigInteger.valueOf(index);
                        if (!indexList.contains(indexBi))
                        {   //just make sure they didn't already add this one
                            indexList.add(indexBi);
                        }
                    }
                    else
                    {
                        indexList = null;
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            indexList = null;
        }

        return indexList;
    }

    private void displayTokenscriptView(TicketRange range, AssetDefinitionService assetService, View activity, Context ctx, boolean iconified)
    {
        //get webview
        Web3TokenView tokenView = activity.findViewById(R.id.web3_tokenview);
        ProgressBar waitSpinner = activity.findViewById(R.id.progress_element);
        activity.findViewById(R.id.layout_webwrapper).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.layout_legacy).setVisibility(View.GONE);

        waitSpinner.setVisibility(View.VISIBLE);
        BigInteger tokenId = range.tokenIds.get(0);

        final StringBuilder attrs = assetService.getTokenAttrs(this, tokenId, range.tokenIds.size());

        assetService.resolveAttrs(this, tokenId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(attr -> onAttr(attr, attrs), throwable -> onError(throwable, ctx, assetService, attrs, waitSpinner, tokenView, iconified),
                        () -> displayTicket(ctx, assetService, attrs, waitSpinner, tokenView, iconified))
                .isDisposed();
    }

    private void displayTicket(Context ctx, AssetDefinitionService assetService, StringBuilder attrs, ProgressBar waitSpinner, Web3TokenView tokenView, boolean iconified)
    {
        waitSpinner.setVisibility(View.GONE);
        tokenView.setVisibility(View.VISIBLE);

        String view = assetService.getTokenView(tokenInfo.chainId, getAddress(), iconified ? "view-iconified" : "view");
        String style = assetService.getTokenView(tokenInfo.chainId, getAddress(), "style");
        String viewData = tokenView.injectWeb3TokenInit(ctx, view, attrs.toString());
        viewData = tokenView.injectStyleData(viewData, style); //style injected last so it comes first

        String base64 = android.util.Base64.encodeToString(viewData.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        tokenView.loadData(base64, "text/html; charset=utf-8", "base64");
    }

    private void onError(Throwable throwable, Context ctx, AssetDefinitionService assetService, StringBuilder attrs, ProgressBar waitSpinner, Web3TokenView tokenView, boolean iconified)
    {
        throwable.printStackTrace();
        displayTicket(ctx, assetService, attrs, waitSpinner, tokenView, iconified);
    }

    private void onAttr(TokenScriptResult.Attribute attribute, StringBuilder attrs)
    {
        TokenScriptResult.addPair(attrs, attribute.id, attribute.text);
    }

    public void displayTicketHolder(TicketRange range, View activity, AssetDefinitionService assetService, Context ctx)
    {
        displayTicketHolder(range, activity, assetService, ctx, false);
    }

    /**
     * This is a single method that populates any instance of graphic ticket anywhere
     *
     * @param range
     * @param activity
     * @param assetService
     * @param ctx needed to create date/time format objects
     */
    @Override
    public void displayTicketHolder(TicketRange range, View activity, AssetDefinitionService assetService, Context ctx, boolean iconified)
    {
        TokenDefinition td = assetService.getAssetDefinition(tokenInfo.chainId, tokenInfo.address);
        if (td != null)
        {
            //use webview
            displayTokenscriptView(range, assetService, activity, ctx, iconified);
        }
        else
        {
            activity.findViewById(R.id.layout_legacy).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.layout_webwrapper).setVisibility(View.GONE);

            TextView amount = activity.findViewById(R.id.amount);
            TextView name = activity.findViewById(R.id.name);

            String nameStr = getTokenTitle();
            String seatCount = String.format(Locale.getDefault(), "x%d", range.tokenIds.size());

            name.setText(nameStr);
            amount.setText(seatCount);
        }
    }

    public void checkIsMatchedInXML(AssetDefinitionService assetService)
    {
        isMatchedInXML = assetService.hasDefinition(tokenInfo.chainId, tokenInfo.address);
    }

    @Override
    public boolean isMatchedInXML()
    {
        return isMatchedInXML;
    }

    @Override
    public Function getTransferFunction(String to, List<BigInteger> tokenIndices) throws NumberFormatException
    {
        return new Function(
                "transfer",
                Arrays.asList(new org.web3j.abi.datatypes.Address(to),
                        getDynArray(tokenIndices)
                ),
                Collections.emptyList());
    }

    @Override
    public boolean contractTypeValid()
    {
        switch (contractType)
        {
            case ERC875:
            case ERC875_LEGACY:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected org.web3j.abi.datatypes.DynamicArray getDynArray(List<BigInteger> indices)
    {
        DynamicArray dynArray;

        switch (contractType)
        {
            case ERC875_LEGACY:
                dynArray = new org.web3j.abi.datatypes.DynamicArray<>(
                        org.web3j.abi.datatypes.generated.Uint16.class,
                        org.web3j.abi.Utils.typeMap(indices, org.web3j.abi.datatypes.generated.Uint16.class));
                break;
            case ERC875:
            default:
                dynArray = new org.web3j.abi.datatypes.DynamicArray<>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(indices, org.web3j.abi.datatypes.generated.Uint256.class));
                break;
        }

        return dynArray;
    }

    /**
     * Refresh transactions for TokenScript enabled tokens at startup, once per 5 minutes
     * and finally if the user does a refresh
     *
     * TODO: This heuristic becomes redundant once we enable event support
     * @return token requires a transaction refresh
     */
    @Override
    public boolean requiresTransactionRefresh()
    {
        boolean requiresUpdate = balanceChanged;
        balanceChanged = false;
        if (hasTokenScript && hasPositiveBalance())
        {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTxCheck > 5*60*1000) //need to check transactions for function updates, at startup and every 5 minutes is good
            {
                lastTxCheck = currentTime;
                refreshCheck = false;
                requiresUpdate = true;
            }
        }

        return requiresUpdate;
    }

    @Override
    public int interfaceOrdinal()
    {
        return contractType.ordinal();
    }

    @Override
    public BigInteger getTokenID(int index)
    {
        if (balanceArray.size() > index && index >= 0) return balanceArray.get(index);
        else return BigInteger.valueOf(-1);
    }

    @Override
    public boolean isToken() {
        return false;
    }

    @Override
    protected String addSuffix(String result, Transaction transaction)
    {
        return result;
    }

    @Override
    public boolean checkIntrinsicType()
    {
        return (contractType == ContractType.ERC875 || contractType == ContractType.ERC875_LEGACY);
    }

    @Override
    public boolean hasArrayBalance()
    {
        return true;
    }

    @Override
    public List<BigInteger> getArrayBalance() { return balanceArray; }

    @Override
    public List<BigInteger> getNonZeroArrayBalance()
    {
        List<BigInteger> nonZeroValues = new ArrayList<>();
        for (BigInteger value : balanceArray) if (value.compareTo(BigInteger.ZERO) != 0 && !nonZeroValues.contains(value)) nonZeroValues.add(value);
        return nonZeroValues;
    }

    /**
     * Detect a change of balance for ERC875 balance
     * @param balanceArray
     * @return
     */
    @Override
    public boolean checkBalanceChange(List<BigInteger> balanceArray)
    {
        if (balanceArray.size() != this.balanceArray.size()) return true; //quick check for new tokens
        for (int index = 0; index < balanceArray.size(); index++) //see if spawnable token ID has changed
        {
            if (!balanceArray.get(index).equals(this.balanceArray.get(index))) return true;
        }
        return false;
    }

    @Override
    public boolean getIsSent(Transaction transaction)
    {
        boolean isSent = true;
        TransactionOperation operation = transaction.operations == null
                || transaction.operations.length == 0 ? null : transaction.operations[0];

        if (operation != null && operation.contract instanceof ERC875ContractTransaction)
        {
            ERC875ContractTransaction ct = (ERC875ContractTransaction) operation.contract;
            if (ct.type > 0) isSent = false;
        }
        return isSent;
    }

    @Override
    public boolean isERC875() { return true; }
    public boolean isNonFungible() { return true; }

    @Override
    public boolean groupWithToken(TicketRange currentGroupingRange, TicketRangeElement newElement, long currentGroupTime)
    {
        if (currentGroupingRange.tokenIds.size() == 0) return false;

        return currentGroupingRange.tokenIds.get(0)
                .equals(newElement.id) || (newElement.time != 0 && newElement.time == currentGroupTime);
    }

    /**
     * This function should return a String list of IDs suitable for submission to the token's transfer function
     * For ERC875 it is a list of indices, so convert this list of TokenIDs to indices
     * @param CSVstringIdList
     * @return
     */

    public String getTransferListFormat(String CSVstringIdList)
    {
        List<BigInteger> indexList = ticketIdStringToIndexList(CSVstringIdList); //convert the list of tokenID to indices.
        return bigIntListToString(indexList, true);
    }

    /**
     * This function takes a list of tokenIds, and returns a BigInteger list suitable for this token's transfer function
     * For ERC875 it is a list of indices, so convert this list of TokenIDs to indices
     * @param tokenIds
     * @return
     */
    @Override
    public List<BigInteger> getTransferListFormat(List<BigInteger> tokenIds)
    {
        return tokenIdsToTokenIndices(tokenIds);
    }
}
